package com.officeplatform.onlyoffice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlyOfficeConfig {

    private OnlyOfficeDocument document;

    private String documentType;

    private OnlyOfficeEditorConfig editorConfig;

    private String token;

}
