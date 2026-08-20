package com.ecommerce.project.payload;

import com.ecommerce.project.model.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long userId;
    private String username;
    private String email;
    private String password;
    private Set<Role> roles = new HashSet<>();
    private boolean locked;

    private AddressDTO address;
    private CartDTO cart;
}
