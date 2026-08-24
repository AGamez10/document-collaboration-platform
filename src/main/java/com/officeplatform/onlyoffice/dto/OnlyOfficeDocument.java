package com.officeplatform.onlyoffice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlyOfficeDocument {

    private String fileType;

    private String key;

    private String title;

    private String url;

    private OnlyOfficePermissions permissions;

}
