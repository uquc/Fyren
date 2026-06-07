package com.Fyren.auth.model;

/**
 * 注册请求 DTO。
 */
public class RegisterRequest {
    public String username;
    public String password;

    public RegisterRequest() {}

    public RegisterRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
