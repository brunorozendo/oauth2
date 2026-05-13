package com.brunorozendo.oauth2.config;

public class JwtPrincipal {

    private final String sub;
    private final String email;
    private final String name;
    private final String picture;
    private final boolean emailVerified;
    private final long expiresAtEpochMilli;

    public JwtPrincipal(String sub, String email, String name, String picture,
                        boolean emailVerified, long expiresAtEpochMilli) {
        this.sub = sub;
        this.email = email;
        this.name = name;
        this.picture = picture;
        this.emailVerified = emailVerified;
        this.expiresAtEpochMilli = expiresAtEpochMilli;
    }

    public String getSub() { return sub; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPicture() { return picture; }
    public boolean isEmailVerified() { return emailVerified; }
    public long getExpiresAtEpochMilli() { return expiresAtEpochMilli; }
}
