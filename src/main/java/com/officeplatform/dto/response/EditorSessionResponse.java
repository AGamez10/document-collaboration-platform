package com.officeplatform.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditorSessionResponse {

    private Long id;

    private Long fileId;

    private String fileName;

    private String userId;

    private String userName;

    private Long apiKeyId;

    private String apiKeyName;

    private LocalDateTime openedAt;

    private LocalDateTime closedAt;

}
