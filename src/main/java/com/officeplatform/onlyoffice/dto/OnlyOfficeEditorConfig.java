package com.officeplatform.onlyoffice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlyOfficeEditorConfig {

    private String callbackUrl;

    private String lang;

    private String mode;

    private Object user;

}
