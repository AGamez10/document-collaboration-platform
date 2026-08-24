package com.officeplatform.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoveFolderRequest {

    /** Destination parent folder id. Null means move to the root. */
    private Long parentId;

}
