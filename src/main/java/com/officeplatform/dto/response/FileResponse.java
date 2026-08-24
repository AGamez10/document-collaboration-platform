package com.officeplatform.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileResponse {

    private Long id;

    private String uuid;

    private String originalFileName;

    private String mimeType;

    private Long size;

    private Long folderId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    private String createdByName;

    private String createdByUserId;

    private String updatedByName;

    /** True when this resource has share permissions attached (restricted). Set by the shared-space filter. */
    private boolean restricted;

}
