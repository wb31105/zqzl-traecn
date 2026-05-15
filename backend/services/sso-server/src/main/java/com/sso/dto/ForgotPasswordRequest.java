package com.sso.dto;

import lombok.Data;
import javax.validation.constraints.*;

@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6-100个字符之间")
    private String newPassword;

    private String captcha;
    private String captchaKey;
}
