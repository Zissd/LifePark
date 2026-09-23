package com.lifepark.gateway.auth;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 注册全局过滤器配置类


//1.登录校验：对所有接口进行拦截，通过 StpUtil.checkLogin() 方法校验是否已经登录
// 这里仅排除掉登录接口、验证码发送接口，这两个接口无需登录就能被请求；

//2.权限认证：权限认证部分，笔记发布接口，需要校验是否拥有笔记发布的权限。
@Configuration
public class SaTokenConfigure {
    // 注册 Sa-Token全局过滤器
    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                // 拦截地址
                .addInclude("/**")    /* 拦截全部path */
                // 鉴权方法：每次访问进入
                .setAuth(obj -> {
                    // 登录校验
                    SaRouter.match("/**") // 拦截所有路由
                            .notMatch("/auth/login") // 排除登录接口
                            .notMatch("/auth/verification/code/send") // 排除验证码发送接口
                            .check(r -> StpUtil.checkLogin()) // 校验是否登录
                    ;

                    // 权限认证 -- 不同模块, 校验不同权限，校验角色与权限
// 在 SaToken框架中，是通过StpInterface的实现类来拉取相关数据的，即之前创建的StpInterfaceImpl类。
// 实现类中获取redis中事先存储的角色权限列表（每次项目加载自动存入，PushRolePermissions2RedisRunner）

//SaToken 实际上会主动调用 StpInterfaceImpl.getPermissionList() 方法，
// 去查询当前用户实际拥有的权限集合，并与之做对比来做判断
// SaToken 实际上会主动调用 StpInterfaceImpl.getRoleList() 方法，
// 去查询当前用户实际拥有的角色集合，并与之做对比来做判断
// 实现权限认证与角色认证
                    SaRouter.match("/auth/user/logout", r -> StpUtil.checkPermission("app:note:publish"));
                    //SaRouter.match("/auth/user/logout", r -> StpUtil.checkPermission("user"));
                    //SaRouter.match("/auth/user/logout", r -> StpUtil.checkRole("admin"));
                    // SaRouter.match("/auth/user/logout", r -> StpUtil.checkPermission("admin"));
                    // SaRouter.match("/goods/**", r -> StpUtil.checkPermission("goods"));
                    // SaRouter.match("/orders/**", r -> StpUtil.checkPermission("orders"));

                    // 更多匹配 ...  */
                })
                // 异常处理方法：每次setAuth函数出现异常时进入
                .setError(e -> {
                    // return SaResult.error(e.getMessage());
                    // 手动抛出异常，抛给全局异常处理器
                    if (e instanceof NotLoginException) { // 未登录异常
                        throw new NotLoginException(e.getMessage(), null, null);
                    } else if (e instanceof NotPermissionException || e instanceof NotRoleException) { // 权限不足，或不具备角色，统一抛出权限不足异常
                        throw new NotPermissionException(e.getMessage());
                    } else { // 其他异常，则抛出一个运行时异常
                        throw new RuntimeException(e.getMessage());
                    }
                })
                ;
    }
}

