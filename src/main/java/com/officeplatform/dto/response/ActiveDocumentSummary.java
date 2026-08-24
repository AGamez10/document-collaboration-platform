package com.officeplatform.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActiveDocumentSummary {

    private String key;

    private String fileName;

    private List<String> users;

    private LocalDateTime openedAt;

}
