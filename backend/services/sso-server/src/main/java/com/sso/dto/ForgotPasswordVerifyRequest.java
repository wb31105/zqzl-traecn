package com.sso.dto;

import lombok.Data;
import javax.validation.constraints.*;

@Data
public class ForgotPasswordVerifyRequest {

    @NotBlank(message = "请输入用户名、手机号或邮箱")
    private String identifier;

    @NotBlank(message = "验证码不能为空")
    private String captcha;

    @NotBlank(message = "验证码Key不能为空")
    private String captchaKey;
}
