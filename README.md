# LifePark

基于 Spring Cloud 微服务架构的社区项目（仿小红书），涵盖网关鉴权、用户、笔记、评论、计数、搜索、对象存储等完整链路。

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 基础框架 | Spring Boot 3.0.2 / Spring Cloud 2022.0.0 / Spring Cloud Alibaba 2022.0.0.0 |
| 注册与配置 | Nacos |
| 网关 | Spring Cloud Gateway |
| 认证鉴权 | Sa-Token |
| 服务调用 | OpenFeign |
| 数据库 | MySQL + MyBatis + Druid |
| 缓存 | Redis（Lettuce） |
| 消息队列 | RocketMQ |
| 搜索引擎 | Elasticsearch + Canal |
| 对象存储 | 阿里云 OSS / MinIO |
| 定时任务 | XXL-Job |
| 工具库 | Hutool、Guava、Caffeine、Lombok、TransmittableThreadLocal |

## 服务划分

| 服务 | 端口 | 职责 |
| --- | --- | --- |
| lifepark-gateway | 8000 | 网关，负责路由转发与接口鉴权 |
| lifepark-auth | 8080 | 认证服务，处理用户登录、注册、账号注销等 |
| lifepark-oss | 8081 | 对象存储服务 |
| lifepark-user | 8082 | 用户服务 |
| lifepark-kv | 8084 | Key-Value 键值存储服务 |
| lifepark-distributed-id-generator | 8085 | 分布式 ID 生成服务 |
| lifepark-note | 8086 | 笔记服务 |
| lifepark-user-relation | 8087 | 用户关系服务 |
| lifepark-count | 8090 | 计数服务 |
| lifepark-data-align | 8091 | 数据对齐服务 |
| lifepark-search | 8092 | 搜索服务 |
| lifepark-comment | 8093 | 评论服务 |
| lifepark-framework | — | 平台基础设施层，封装通用能力供各业务线复用 |

服务间采用 `-api` / `-biz` 分层：`-api` 暴露 RPC 接口与 DTO 供其他服务依赖，`-biz` 承载业务实现，避免服务间直接依赖具体实现。

## 核心实现

### 网关服务 & 认证服务

- 搭建网关实现路由转发、用户 ID 透传：网关在转发请求时通过全局过滤器将用户 ID 写入请求头，透传给下游服务，配合上下文组件实现全链路用户信息传递。
- 提供短信登录方式，并通过 Sa-Token 实现登录校验与权限控制。

### 用户服务

- 使用 **Redis + Caffeine 构建二级缓存**，支持对用户、笔记信息的高并发读写，同时有效防止缓存击穿。
- 通过 **Redis ZSet** 缓存用户的关注 / 粉丝列表，并结合 **顺序 MQ 异步落库**，实现高并发写。

### 笔记服务

- 使用**布隆过滤器**判断用户是否点赞 / 收藏，避免无效请求穿透到存储层。
- 消费者使用 **Guava RateLimiter 令牌桶**实现削峰，保证高并发写入下的稳定性。

### 计数服务

- 实现用户维度的发布笔记数、关注数、粉丝数，与笔记维度的点赞数、收藏数等计数需求。
- 计数变更通过 RocketMQ 异步解耦，配合数据对齐服务定时校准，保证最终一致性。

### 搜索服务

- 结合 **Canal 监听 MySQL**，实现对 Elasticsearch 笔记、用户增量索引的实时更新，确保数据一致性。

## 环境准备

启动前需要准备以下中间件：

- Nacos（注册中心 + 配置中心）
- MySQL
- Redis
- RocketMQ
- Elasticsearch
- XXL-Job（数据对齐服务的定时任务调度）

## 快速开始

### 1. 初始化中间件

| 组件 | 需要创建的项 |
| --- | --- |
| MySQL | 数据库 `lifepark`，导入相关表结构 |
| Nacos | 命名空间 `lifepark`，并导入各服务的配置 |
| RocketMQ | 无需预建，消费者组统一以 `lifepark_group_` 开头 |
| Elasticsearch | 笔记、用户索引（`note` / `user`） |
| XXL-Job | 执行器 `xxl-job-executor-lifepark` |
| Canal | 订阅规则 `lifepark.t_note,lifepark.t_user` |

### 2. 修改配置

各服务的配置文件位于 `lifepark-<服务名>/src/main/resources/config/` 下，默认使用 `dev` 环境。请按自己的环境填写：

- `application-dev.yml`：MySQL 连接信息（库名 `lifepark`）、Redis 地址与密码
- 阿里云相关配置：短信服务的 `accessKeyId` / `accessKeySecret`、OSS 的 `accessKey` / `secretKey`（当前为占位符，需替换为你自己的）
- `bootstrap.yml`：Nacos 服务地址、命名空间 `lifepark`
- 对象存储：如使用 MinIO 需配置 `endpoint`、`accessKey`、`secretKey`

### 3. 启动服务

先启动 Nacos，再依次启动各业务服务，最后启动网关服务 `lifepark-gateway`。所有请求统一由网关（8000 端口）转发。

## 项目结构

```
lifepark
├── lifepark-framework              # 基础设施层（公共组件、starter）
│   ├── lifepark-common                # 通用枚举、工具类
│   ├── lifepark-spring-boot-starter-biz-context     # 上下文组件
│   ├── lifepark-spring-boot-starter-biz-operationlog # 接口日志组件
│   └── lifepark-spring-boot-starter-jackson         # Jackson 配置
├── lifepark-gateway                # 网关
├── lifepark-auth                   # 认证
├── lifepark-user                   # 用户
├── lifepark-note                   # 笔记
├── lifepark-comment               # 评论
├── lifepark-count                  # 计数
├── lifepark-user-relation          # 用户关系
├── lifepark-search                 # 搜索
├── lifepark-kv                     # 键值存储
├── lifepark-oss                    # 对象存储
├── lifepark-data-align             # 数据对齐
└── lifepark-distributed-id-generator # 分布式 ID 生成
```