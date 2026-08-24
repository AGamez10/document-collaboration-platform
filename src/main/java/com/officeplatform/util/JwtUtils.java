package com.officeplatform.util;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

public final class JwtUtils {

    private JwtUtils() {
    }

    public static String generateToken(String secret, Map<String, Object> claims) {
        return Jwts.builder()
            .claims(claims)
            .issuedAt(new Date())
            .signWith(buildKey(secret), Jwts.SIG.HS256)
            .compact();
    }

    public static Claims parseToken(String secret, String token) {
        return Jwts.parser()
            .verifyWith(buildKey(secret))
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private static SecretKey buildKey(String secret) {
        // Use raw UTF-8 bytes to match OnlyOffice's node-jsonwebtoken behavior.
        // SecretKeySpec bypasses jjwt's 256-bit minimum enforcement — HMAC-SHA256
        // has no minimum key length in the cryptographic spec (RFC 2104).
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

}
