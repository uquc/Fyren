package com.Fyren.auth.model;

/**
 * 登录/刷新 Token 响应 DTO。
 */
public class TokenResponse {
    public String accessToken;
    public String refreshToken;
    public int userId;
    public String username;
    public int mmr;

    public TokenResponse() {}

    public TokenResponse(String accessToken, String refreshToken, int userId, String username, int mmr) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.username = username;
        this.mmr = mmr;
    }
}
