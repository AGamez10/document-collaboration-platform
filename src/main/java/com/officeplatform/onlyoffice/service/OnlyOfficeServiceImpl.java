package com.officeplatform.onlyoffice.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.JwtException;

import com.officeplatform.exception.OnlyOfficeException;
import com.officeplatform.onlyoffice.dto.OnlyOfficeCallbackRequest;
import com.officeplatform.onlyoffice.dto.OnlyOfficeConfig;
import com.officeplatform.security.jwt.JwtService;
import com.officeplatform.util.UrlUtils;

@Service
public class OnlyOfficeServiceImpl implements OnlyOfficeService {

    private static final String COMMAND_SERVICE_PATH = "/coauthoring/CommandService.ashx";

    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final RestTemplate restTemplate;
    private final String documentServerUrl;

    public OnlyOfficeServiceImpl(
            ObjectMapper objectMapper,
            JwtService jwtService,
            RestTemplate restTemplate,
            @Value("${office-platform.onlyoffice.document-server-url}") String documentServerUrl) {
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
        this.restTemplate = restTemplate;
        this.documentServerUrl = documentServerUrl;
    }

    @Override
    public String signConfig(OnlyOfficeConfig config) {
        Map<String, Object> claims = objectMapper.convertValue(config, new TypeReference<Map<String, Object>>() {
        });
        return jwtService.sign(claims);
    }

    @Override
    public void validateCallback(OnlyOfficeCallbackRequest callback) {
        if (callback.getToken() == null || callback.getToken().isBlank()) {
            throw new OnlyOfficeException("El callback de OnlyOffice no incluye token de validación");
        }
        try {
            jwtService.validate(callback.getToken());
        } catch (JwtException e) {
            throw new OnlyOfficeException("El token del callback de OnlyOffice no es válido", e);
        }
    }

    @Override
    public void dropUser(String documentKey, String userId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("c", "drop");
        payload.put("key", documentKey);
        payload.put("users", List.of(userId == null ? "" : userId));

        // The Command Service authenticates the request with a JWT of the payload, carried both in
        // the body ("token") and the Authorization header, mirroring how callbacks are signed here.
        String token = jwtService.sign(payload);
        Map<String, Object> body = new HashMap<>(payload);
        body.put("token", token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        String url = UrlUtils.joinPath(documentServerUrl, COMMAND_SERVICE_PATH);
        ResponseEntity<String> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);

        String responseBody = response.getBody();
        if (responseBody == null || responseBody.isBlank()) {
            return;
        }
        Object error;
        try {
            Map<String, Object> result =
                    objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
            error = result.get("error");
        } catch (JsonProcessingException e) {
            throw new OnlyOfficeException("No se pudo interpretar la respuesta del Command Service de OnlyOffice", e);
        }
        // error 0 (or absent) means success; anything else (e.g. 1 = document/key not found) is a failure.
        if (error != null && !"0".equals(String.valueOf(error))) {
            throw new OnlyOfficeException("OnlyOffice rechazó el comando 'drop' (error " + error + ")");
        }
    }

}
