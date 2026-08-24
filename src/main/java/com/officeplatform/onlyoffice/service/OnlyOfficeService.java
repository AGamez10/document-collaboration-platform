package com.officeplatform.onlyoffice.service;

import com.officeplatform.onlyoffice.dto.OnlyOfficeCallbackRequest;
import com.officeplatform.onlyoffice.dto.OnlyOfficeConfig;

public interface OnlyOfficeService {

    String signConfig(OnlyOfficeConfig config);

    void validateCallback(OnlyOfficeCallbackRequest callback);

    /**
     * Forcibly disconnects a user from an open document via the OnlyOffice Command Service
     * ("drop"). Used by the admin panel to actually kick a user out of a live editing session,
     * not just mark it closed in the DB. Throws {@link com.officeplatform.exception.OnlyOfficeException}
     * if the Document Server is unreachable or rejects the command.
     */
    void dropUser(String documentKey, String userId);

}
