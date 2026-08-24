package com.officeplatform.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private Long totalFiles;

    private Long activeApiKeys;

    private Long storageUsedBytes;

    private Long filesCreatedToday;

    private Long filesCreatedThisWeek;

    private Long filesCreatedThisMonth;

    private List<DailyActivityCount> activityLast30Days;

    private Long storageLimit;

    private List<TopApiKeyUsage> topApiKeys;

}
