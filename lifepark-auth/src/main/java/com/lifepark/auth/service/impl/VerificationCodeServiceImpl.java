package com.lifepark.auth.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.lifepark.framework.common.exception.BizException;
import com.lifepark.framework.common.response.Response;
import com.lifepark.auth.constant.RedisKeyConstants;
import com.lifepark.auth.enums.ResponseCodeEnum;
import com.lifepark.auth.model.vo.verificationcode.SendVerificationCodeReqVO;
import com.lifepark.auth.service.VerificationCodeService;
import com.lifepark.auth.sms.AliyunSmsHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VerificationCodeServiceImpl implements VerificationCodeService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Resource
    private AliyunSmsHelper aliyunSmsHelper;


    /**
     * 发送验证码
     * @param sendVerificationCodeReqVO
     * @return
     */
    @Override
    public Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO) {
        //从VO对象中获取手机号
        String phone = sendVerificationCodeReqVO.getPhone();
        //在redis中查找是否存在此手机号，若存在,则提示不要频繁操作
        String key = RedisKeyConstants.buildVerificationCodeKey(phone);
        Boolean isSent = redisTemplate.hasKey(key);
        //这里Boolean是包装类，可能为True,False,null
        //为null时会出现空指针异常，所以用Boolean.TRUE.equals(isSent)
        if(Boolean.TRUE.equals(isSent)){
            // 若之前发送的验证码未过期，则提示发送频繁
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
        }
        //redis中不存在，调用阿里云服务，发送短信，并将短信验证码保存到redis中，三分钟有效期
        //随机生成六位数验证码
        String verificationCode = RandomUtil.randomNumbers(6);
        log.info("==> 手机号: {}, 已生成验证码：【{}】", phone, verificationCode);
        // 调用第三方短信发送服务
        threadPoolTaskExecutor.submit(() -> {
            String signName = "速通互联验证码"; // 签名，个人测试签名无法修改
            String templateCode = "100001"; // 短信模板编码
            // 短信模板参数，code 表示要发送的验证码；min 表示验证码有时间时长，即 3 分钟
            String templateParam = String.format("{\"code\":\"%s\",\"min\":\"3\"}", verificationCode);
            aliyunSmsHelper.sendMessage(signName, templateCode, phone, templateParam);
        });

        // 保存验证码到redis中，三分钟有效期
        redisTemplate.opsForValue().set(key,verificationCode ,3 , TimeUnit.MINUTES);;
        return Response.success();
    }
}
