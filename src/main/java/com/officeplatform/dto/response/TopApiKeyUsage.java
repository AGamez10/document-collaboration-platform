package com.officeplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopApiKeyUsage {

    private String name;

    private long fileCount;

    private long storageUsed;

}
