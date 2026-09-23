package com.lifepark.user.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

//入参--------修改用户信息

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateUserInfoReqVO {

    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    //头像
    private MultipartFile avatar;

    //昵称
    private String nickname;

    //LifePark ID
    private String lifeparkId;

    // 性别
    private Integer sex;

    // 生日
    private LocalDate birthday;

    // 个人介绍
    private String introduction;

    //背景图
    private MultipartFile backgroundImg;
}

