package com.officeplatform.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlyOfficeStatusResponse {

    private long activeConnections;

    private int maxConnections;

    private int usage;

    private List<ActiveDocumentSummary> documents;

}
