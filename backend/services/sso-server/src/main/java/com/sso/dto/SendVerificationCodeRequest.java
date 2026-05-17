package com.sso.dto;

import lombok.Data;
import javax.validation.constraints.*;

@Data
public class SendVerificationCodeRequest {

    @Email(message = "邮箱格式不正确")
    private String email;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "类型不能为空")
    @Pattern(regexp = "^(register|forgot_password)$", message = "类型只能是 register 或 forgot_password")
    private String type;
}
