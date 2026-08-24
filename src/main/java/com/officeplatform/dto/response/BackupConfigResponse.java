package com.officeplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupConfigResponse {

    private boolean enabled;

    private int intervalHours;

    private int maxRetainedBackups;

    private String backupDirectory;

    private long diskFreeSpaceBytes;

    private String formattedDiskFreeSpace;

    private long diskTotalSpaceBytes;

    private String formattedDiskTotalSpace;

}
