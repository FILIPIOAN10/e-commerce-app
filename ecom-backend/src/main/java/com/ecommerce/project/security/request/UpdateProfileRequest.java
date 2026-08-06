package com.ecommerce.project.security.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Email
    @Size(max = 50)
    private String email;

    @Size(max = 20)
    private String phone;
}
