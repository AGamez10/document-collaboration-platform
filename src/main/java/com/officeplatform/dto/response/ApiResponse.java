package com.officeplatform.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {

    private Boolean success;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    private String message;

    private T data;

    private Map<String, Object> metadata;

}
