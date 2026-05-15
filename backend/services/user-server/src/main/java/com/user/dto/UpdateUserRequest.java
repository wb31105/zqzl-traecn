package com.user.dto;

import lombok.Data;
import javax.validation.constraints.*;

@Data
public class UpdateUserRequest {

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;

    @Size(max = 20, message = "手机号长度不能超过20个字符")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;

    @Size(max = 500, message = "头像URL长度不能超过500个字符")
    private String avatar;

    @Pattern(regexp = "^(USER|ADMIN)$", message = "角色只能是USER或ADMIN")
    private String role;
}
