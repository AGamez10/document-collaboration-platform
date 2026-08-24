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
public class BackupInfoResponse {

    private String fileName;

    private Long sizeBytes;

    private String formattedSize;

    private Integer filesCount;

    private String type; // "MANUAL" | "AUTOMATICO"

    private LocalDateTime createdAt;

}
