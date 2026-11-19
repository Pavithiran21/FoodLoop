package com.foodloop.auth;

import com.foodloop.user.UserResponse;

/** A new login: the profile and access token for the body, the refresh token for the cookie. */
public record AuthSession(UserResponse user, String accessToken, String refreshToken) {
}
