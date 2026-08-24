package com.officeplatform.onlyoffice.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlyOfficeCallbackRequest {

    private String key;

    private Integer status;

    private String url;

    @JsonProperty("changesurl")
    private String changesUrl;

    private List<String> users;

    private String token;

}
