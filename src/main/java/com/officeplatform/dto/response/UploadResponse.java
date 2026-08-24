package com.officeplatform.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {

    private Long fileId;

    private String uuid;

    private String originalFileName;

    private String mimeType;

    private Long size;

    private LocalDateTime uploadedAt;

}
