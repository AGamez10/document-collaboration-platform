package com.officeplatform.util;

public final class FileUtils {

    private FileUtils() {
    }

    public static String extractExtension(String originalFileName) {
        if (originalFileName == null) {
            return null;
        }
        int lastDotIndex = originalFileName.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == originalFileName.length() - 1) {
            return null;
        }
        return originalFileName.substring(lastDotIndex + 1).toLowerCase();
    }

    public static String generateStoredFileName(String uuid, String extension) {
        if (extension == null || extension.isBlank()) {
            return uuid;
        }
        return uuid + "." + extension;
    }

}
