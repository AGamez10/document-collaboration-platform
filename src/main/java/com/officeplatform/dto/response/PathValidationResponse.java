package com.officeplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of checking a backup destination before committing to it.
 *
 * <p>Reports whether the directory had to be created, because an operator typing a path with a
 * typo would otherwise get a cheerful "valid" for a brand-new empty folder in the wrong place.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PathValidationResponse {

    private boolean valid;
    private String path;
    private String message;
    private boolean created;
    private boolean writable;
    private long freeSpaceBytes;
    private String formattedFreeSpace;
}
