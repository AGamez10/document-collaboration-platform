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
public class ActivityLogResponse {

    private Long id;

    private Long apiKeyId;

    private String apiKeyName;

    private String userId;

    private String userName;

    private String action;

    private Long fileId;

    private String fileName;

    private Long folderId;

    private String folderName;

    private String details;

    private String ipAddress;

    private LocalDateTime timestamp;

}
