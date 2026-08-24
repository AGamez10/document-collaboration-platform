package com.officeplatform.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoveFileRequest {

    /** Destination folder id. Null means move to the root. */
    private Long folderId;

}
