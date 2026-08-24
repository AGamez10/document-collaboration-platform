package com.officeplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Lightweight project (API key) reference used by the share modal's project selector. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {

    private Long id;

    private String projectName;

}
