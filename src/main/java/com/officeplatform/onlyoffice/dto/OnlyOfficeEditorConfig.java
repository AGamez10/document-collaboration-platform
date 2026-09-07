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

    /**
     * Editor behaviour Document Server applies only when asked: macros and plugins are off by
     * default, so the Macros toolbar entry never appeared without this section.
     */
    private OnlyOfficeCustomization customization;

}
