package com.officeplatform.util;

import java.util.Collection;

public final class MimeUtils {

    private MimeUtils() {
    }

    public static boolean isAllowed(String mimeType, Collection<String> allowedMimeTypes) {
        if (mimeType == null || allowedMimeTypes == null) {
            return false;
        }
        return allowedMimeTypes.contains(mimeType);
    }

}
