package com.officeplatform.security.jwt;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;

import com.officeplatform.util.JwtUtils;

@Service
public class JwtService {

    private final String jwtSecret;

    public JwtService(@Value("${office-platform.onlyoffice.jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String sign(Map<String, Object> claims) {
        return JwtUtils.generateToken(jwtSecret, claims);
    }

    public Claims validate(String token) {
        return JwtUtils.parseToken(jwtSecret, token);
    }

}
