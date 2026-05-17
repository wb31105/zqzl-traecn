package com.sso.dto;

import lombok.Data;
import javax.validation.constraints.*;

@Data
public class ForgotPasswordRequest {

    private String username;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6-100个字符之间")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

    @NotBlank(message = "验证码不能为空")
    private String verificationCode;

    @NotBlank(message = "验证令牌不能为空")
    private String verifyToken;

    private String selectedContact;
}
