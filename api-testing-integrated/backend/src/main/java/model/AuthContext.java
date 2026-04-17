package model;

import java.time.Instant;

public class AuthContext {
    public String authenticationType;
    public String tokenUrl;
    public String clientId;
    public String clientSecret;
    public String grantType;
    public String authorizationHeader;
    public String username;
    public String password;

    public String accessToken;
    public String refreshToken;
    public Instant tokenExpiry;
}