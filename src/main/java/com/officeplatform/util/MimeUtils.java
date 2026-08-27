package com.officeplatform.util;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MimeUtils {

    /**
     * Extensions accepted for each allowed MIME type.
     *
     * <p>The declared content type of an upload is chosen by the client and cannot be trusted on
     * its own: sending an executable while declaring {@code application/pdf} passed validation.
     * Requiring the file name extension to agree with the declared type closes that bypass without
     * reading file contents.
     */
    private static final Map<String, Set<String>> EXTENSIONS_BY_MIME_TYPE = Map.of(
            "application/pdf", Set.of("pdf"),
            "application/msword", Set.of("doc"),
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("docx"),
            "application/vnd.ms-excel", Set.of("xls"),
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of("xlsx"),
            "application/vnd.ms-powerpoint", Set.of("ppt"),
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", Set.of("pptx"));

    private MimeUtils() {
    }

    public static boolean isAllowed(String mimeType, Collection<String> allowedMimeTypes) {
        if (mimeType == null || allowedMimeTypes == null) {
            return false;
        }
        return allowedMimeTypes.contains(mimeType);
    }

    /**
     * Whether the file name extension is one of those expected for the declared MIME type.
     *
     * <p>An unknown MIME type is not accepted here; callers check {@link #isAllowed} first, so a
     * type reaching this method without a mapping means the two lists drifted apart and the upload
     * is rejected rather than let through unchecked.
     *
     * @param fileName original file name supplied with the upload
     * @param mimeType content type declared by the client
     */
    public static boolean matchesExtension(String fileName, String mimeType) {
        if (fileName == null || mimeType == null) {
            return false;
        }
        Set<String> expected = EXTENSIONS_BY_MIME_TYPE.get(mimeType);
        if (expected == null) {
            return false;
        }
        String extension = FileUtils.extractExtension(fileName);
        if (extension == null || extension.isBlank()) {
            return false;
        }
        return expected.contains(extension.toLowerCase(Locale.ROOT));
    }

}
