package com.lifepark.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.google.common.base.Preconditions;
import com.lifepark.framework.common.exception.BizException;
import com.lifepark.framework.common.response.Response;
import com.lifepark.auth.constant.RedisKeyConstants;
import com.lifepark.auth.enums.LoginTypeEnum;
import com.lifepark.auth.enums.ResponseCodeEnum;
import com.lifepark.auth.filter.LoginUserContextHolder;
import com.lifepark.auth.model.vo.user.UpdatePasswordReqVO;
import com.lifepark.auth.model.vo.user.UserLoginReqVO;
import com.lifepark.auth.rpc.UserRpcService;
import com.lifepark.auth.service.AuthService;
import com.lifepark.user.dto.resp.FindUserByPhoneRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static com.lifepark.auth.enums.ResponseCodeEnum.PHONE_OR_PASSWORD_ERROR;
import static com.lifepark.auth.enums.ResponseCodeEnum.USER_NOT_FOUND;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private UserRpcService userRpcService;

    /**
     * 登录和注册
     * @param userLoginReqVO
     * @return
     */
    @Override
    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
        //拿到手机号与登录方式
        String phone = userLoginReqVO.getPhone();
        Integer type = userLoginReqVO.getType();
        //调用枚举类的valueof方法，拿到登录方式
        LoginTypeEnum loginTypeEnum = LoginTypeEnum.valueOf(type);
        Long userId = null;
        //判断登录方式。switch-case要加break，否则会执行所有case
        switch (loginTypeEnum){
            case VERIFICATION_CODE:
                //拿到验证码
                String verificationCode = userLoginReqVO.getCode();
                //判断验证码是否为空
                Preconditions.checkArgument
                        (StringUtils.isNotBlank(verificationCode), "验证码不能为空");
//                if(StringUtils.isBlank(verificationCode)){
//                    Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(),
//                            "验证码不能为空");
//                }
                //与redis中的验证码进行对比，是否一致
                //redis中的验证码 在用户获取短信验证码就已经存进去
                String redisKey = RedisKeyConstants.buildVerificationCodeKey(phone);
                String code = (String)redisTemplate.opsForValue().get(redisKey);
                //不一致返回验证码错误
                if (!verificationCode.equals(code)) {
                    throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
                }

                // RPC 调用用户服务，拿到用户ID，
                // 先判断该手机号是否已被注册，若已注册，则直接返回用户 ID,否则注册新用户 ,返回ID
                Long userIdTmp = userRpcService.registerUser(phone);

                // 若调用用户服务，返回的用户 ID 为空，则提示登录失败
                if (Objects.isNull(userIdTmp)) {
                    throw new BizException(ResponseCodeEnum.LOGIN_FAIL);
                }

                userId = userIdTmp;
                break;
            case PASSWORD:
                // RPC 调用用户服务，先判断该手机号是否已被注册,根据手机号在数据库用户表中查询该用户
                //若已注册，则直接返回RspDTO,包括密码与ID，否则返回用户不存在
                FindUserByPhoneRspDTO findUserByPhoneRspDTO =
                        userRpcService.findUserByPhone(phone);
                //如果不存在,则抛出业务异常,返回用户不存在
                if(Objects.isNull(findUserByPhoneRspDTO)){
                    throw new BizException(USER_NOT_FOUND);
                }
                //拿到用户输入的明文密码
                String password = userLoginReqVO.getPassword();
                //与数据库中的加密的密码进行对比
                String password1 = findUserByPhoneRspDTO.getPassword();
                boolean matches = passwordEncoder.matches(password, password1);
                //如果不一致,则抛出业务异常,返回用户名或密码错误
                if (!matches) {
                    throw new BizException(PHONE_OR_PASSWORD_ERROR);
                }
                //拿到用户id 以便SA-token实现用户登录
                userId=findUserByPhoneRspDTO.getId();
                break;
            default:
                break;
        }
        //最后，调用SA-token实现用户登录, 拿到token令牌
        StpUtil.login(userId);
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        //返回token令牌
        return Response.success(tokenInfo.tokenValue);
    }

//    /**
//     * 注册用户
//     * @param phone
//     * @return
//     */
//    @Transactional(rollbackFor = Exception.class)
//    //当类中的方法调用同一个类中的另一个 @Transactional 方法时，事务可能不会生效。
//    //这是因为事务注解是通过 AOP 实现的，而 Spring 的 AOP 代理机制在这种情况下不会被触发。
//    //解决办法是使用编程式事务，具体见5.13 这里暂时不处理
//    public Long registerUser(String phone) {
//        //获取小红书id （由redis每次自增1生成的全局唯一id）
//        Long lifeparkId = redisTemplate.opsForValue()
//                .increment(RedisKeyConstants.LIFEPARK_ID_GENERATOR_KEY);
//        //创建用户
//        UserDO userDO = UserDO.builder()
//                .phone(phone)
//                .lifeparkId(String.valueOf(lifeparkId))
//                .nickname("小红薯" + lifeparkId)
//                .status(StatusEnum.ENABLE.getValue())
//                .createTime(LocalDateTime.now())
//                .updateTime(LocalDateTime.now())
//                .isDeleted(DeletedEnum.NO.getValue())
//                .build();
//        //用户入库
//        userDOMapper.insert(userDO);
//        //得到用户Id，给该用户绑定一个角色，此处绑定此用户角色为普通用户, 普通角色Id为1
//        Long userId = userDO.getId();
//        //创建用户角色
//        UserRoleDO userRoleDO = UserRoleDO.builder()
//                .userId(userId)
//                .roleId(RoleConstants.COMMON_USER_ROLE_ID)
//                .createTime(LocalDateTime.now())
//                .updateTime(LocalDateTime.now())
//                .isDeleted(DeletedEnum.NO.getValue())
//                .build();
//        //用户角色入库
//        userRoleDOMapper.insert(userRoleDO);
//        //将用户的角色id存入redis中
////        List<Long> roles = Lists.newArrayList();
////        roles.add(RoleConstants.COMMON_USER_ROLE_ID);
////        String userRolesKey = RedisKeyConstants.buildUserRoleKey(phone);
////        stringRedisTemplate.opsForValue().set(userRolesKey, JsonUtils.toJsonString(roles));
//
//        //当前用户为普通角色，拿到角色信息
//        RoleDO roleDO = roleDOMapper.selectByPrimaryKey(RoleConstants.COMMON_USER_ROLE_ID);
//        // 将该用户的角色 ID 存入 Redis 中，指定初始容量为 1，这样可以减少在扩容时的性能开销
//        List<String> roles = new ArrayList<>(1);
//        roles.add(roleDO.getRoleKey());
//
//        String userRolesKey = RedisKeyConstants.buildUserRoleKey(userId);
//        redisTemplate.opsForValue().set(userRolesKey, JsonUtils.toJsonString(roles));
//
//        //返回用户id
//        return userId;
//    }

    /**
     * 退出登录
     * @return
     */
    @Override
    public Response<?> logout() {

        // 退出登录 (指定用户 ID)
        //只需要调用SAtoken的logout方法，就会自动把redis中的token删除
        //登录时调用了SAtoken的login方法，生成了用户id对应的token与session，存入了redis中

        //从过滤器 + ThreadLocal 中拿到当前用户id
        Long userId = LoginUserContextHolder.getUserId();
        StpUtil.logout(userId);

        return Response.success();
    }

    /**
     * 修改密码
     * @param updatePasswordReqVO
     * @return
     */
    @Override
    public Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO) {
        //拿到用户的明文密码
        String newPassword = updatePasswordReqVO.getNewPassword();
        //对明文密码进行加密
        String encode = passwordEncoder.encode(newPassword);
        //RPC--调用用户微服务修改密码
        userRpcService.updatePassword(encode);

        return Response.success();
    }

}
