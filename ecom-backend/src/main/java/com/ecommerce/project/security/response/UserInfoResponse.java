package com.ecommerce.project.security.response;

import java.util.List;

public class UserInfoResponse {

    private Long id;
    private String jwtToken;

    private String username;
    private String email;
    private List<String> roles;
    private String phone;
    private String avatarUrl;



    public UserInfoResponse(Long id, String username, List<String> roles,String email,String jwtToken) {
        this.id = id;
        this.username = username;
        this.roles = roles;
        this.email=email;
        this.jwtToken=jwtToken;
    }

    public UserInfoResponse(Long id, String username, List<String> roles, String email, String jwtToken, String phone, String avatarUrl) {
        this.id = id;
        this.username = username;
        this.roles = roles;
        this.email = email;
        this.jwtToken = jwtToken;
        this.phone = phone;
        this.avatarUrl = avatarUrl;
    }

    public UserInfoResponse(Long id, String username, List<String> roles) {
        this.id = id;
        this.username = username;
        this.roles = roles;
    }

    public String getJwtToken() {
        return jwtToken;
    }

    public UserInfoResponse setJwtToken(String jwtToken) {
        this.jwtToken = jwtToken;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public UserInfoResponse setUsername(String username) {
        this.username = username;
        return this;
    }

    public List<String> getRoles() {
        return roles;
    }

    public UserInfoResponse setRoles(List<String> roles) {
        this.roles = roles;
        return this;
    }

    public Long getId() {
        return id;
    }

    public UserInfoResponse setId(Long id) {
        this.id = id;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public UserInfoResponse setEmail(String email) {
        this.email = email;
        return this;
    }

    public String getPhone() {
        return phone;
    }

    public UserInfoResponse setPhone(String phone) {
        this.phone = phone;
        return this;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public UserInfoResponse setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
        return this;
    }
}
