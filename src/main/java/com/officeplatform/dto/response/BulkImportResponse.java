package com.officeplatform.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Lo que hizo una migración masiva, para poder contarlo y para poder auditarlo después. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportResponse {

    private String sourceDirectoryPath;

    private int foldersCreated;

    private int filesImported;

    /** Basura de Windows, ejecutables y tipos no permitidos: se cuentan para que nadie los extrañe. */
    private int filesSkipped;

    private long totalBytesImported;

    private long durationMs;

    private List<String> warnings;

}
