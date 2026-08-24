package com.officeplatform.exception;

public class ApiKeyNotFoundException extends RuntimeException {

    private final Long apiKeyId;

    public ApiKeyNotFoundException(Long apiKeyId) {
        super("La API key con ID " + apiKeyId + " no existe");
        this.apiKeyId = apiKeyId;
    }

    public Long getApiKeyId() {
        return apiKeyId;
    }

}
