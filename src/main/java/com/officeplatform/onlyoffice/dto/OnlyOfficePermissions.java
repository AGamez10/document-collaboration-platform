package com.officeplatform.onlyoffice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlyOfficePermissions {

    private Boolean edit;

    private Boolean download;

    private Boolean print;

    private Boolean comment;

    private Boolean fillForms;

    private Boolean review;

}
