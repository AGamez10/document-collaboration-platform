package com.officeplatform.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnownUserResponse {

    private Long id;

    private Long apiKeyId;

    private String apiKeyName;

    private String userId;

    private String displayName;

    private String role;

    private LocalDateTime firstSeenAt;

    private LocalDateTime lastSeenAt;

    private java.util.List<String> consumingApps;

}
