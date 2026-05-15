package com.user.dto;

import lombok.Data;
import javax.validation.constraints.*;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6-100个字符之间")
    private String newPassword;
}
