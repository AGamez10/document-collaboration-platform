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


    /**
     * Tamano legible para una persona: "1.50 GB" en lugar de 1610612736.
     *
     * <p>Vivia como metodo privado en BackupServiceImpl. Se promueve acá al necesitarlo tambien
     * los mensajes de cuota, porque dos copias del mismo redondeo terminan divergiendo y el
     * usuario ve un tamano distinto segun por donde mire.
     */
    public static String formatBytes(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        final String[] units = { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        double size = bytes / Math.pow(1024, digitGroups);
        return String.format(java.util.Locale.ROOT, "%.2f %s", size, units[digitGroups]);
    }

}
