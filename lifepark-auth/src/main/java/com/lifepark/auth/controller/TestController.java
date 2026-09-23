package com.lifepark.auth.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.lifepark.framework.biz.operationlog.aspect.ApiOperationLog;
import com.lifepark.framework.common.response.Response;
import com.lifepark.auth.alarm.AlarmInterface;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * @description: 测试接口
 **/
@RestController
public class TestController {

    @Resource
    private AlarmInterface alarm;


    @GetMapping("/alarm")
    public String sendAlarm() {
        alarm.send("系统出错啦，请尽快上线排查解决问题！");
        return "alarm success";
    }

    @NacosValue(value = "${rate-limit.api.limit}", autoRefreshed = true)
    private Integer limit;

    @GetMapping("/test")
    public String test() {
        return "当前限流阈值为: " + limit;
    }

    // 测试登录，浏览器访问： http://localhost:8080/user/doLogin?username=zhang&password=123456
    @RequestMapping("/user/doLogin")
    public Response<String> doLogin(@RequestParam String username, @RequestParam String password) {
        if ("zhang".equals(username) &&
                "123456".equals(password)) {
            StpUtil.login(10001);
            return Response.success("登录成功");
        }
        return Response.fail("登录失败");
    }

    // 查询登录状态，浏览器访问： http://localhost:8080/user/isLogin
    @RequestMapping("/user/isLogin")
    public String isLogin() {
        return "当前会话是否登录：" + StpUtil.isLogin();
    }
}

