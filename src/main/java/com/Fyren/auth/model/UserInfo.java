package com.Fyren.auth.model;

/**
 * 用户信息 DTO（/auth/me 响应）。
 */
public class UserInfo {
    public int userId;
    public String username;
    public int mmr;
    public String role;

    public UserInfo() {}

    public UserInfo(int userId, String username, int mmr, String role) {
        this.userId = userId;
        this.username = username;
        this.mmr = mmr;
        this.role = role;
    }
}
