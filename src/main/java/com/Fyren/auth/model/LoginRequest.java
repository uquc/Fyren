package com.Fyren.auth.model;

/**
 * 登录请求 DTO。
 */
public class LoginRequest {
    public String username;
    public String password;

    public LoginRequest() {}

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
