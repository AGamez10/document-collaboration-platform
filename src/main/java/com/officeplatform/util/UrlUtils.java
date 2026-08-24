package com.officeplatform.util;

import java.net.URI;
import java.net.URISyntaxException;

public final class UrlUtils {

    private UrlUtils() {
    }

    public static String joinPath(String baseUrl, String path) {
        if (baseUrl == null || path == null) {
            throw new IllegalArgumentException("baseUrl y path no pueden ser nulos");
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    /**
     * OnlyOffice Document Server construye la URL de descarga del callback usando la
     * misma dirección pública que el navegador usa para cargar el editor (p. ej.
     * localhost:8081). Esa dirección no es alcanzable desde el backend dentro de la red
     * de Docker, así que hay que reemplazar esquema/host/puerto por la dirección interna
     * real del Document Server, preservando el path y el query de la URL original.
     */
    public static String rewriteHost(String originalUrl, String internalBaseUrl) {
        if (originalUrl == null || internalBaseUrl == null) {
            throw new IllegalArgumentException("originalUrl e internalBaseUrl no pueden ser nulos");
        }
        try {
            URI original = new URI(originalUrl);
            URI internalBase = new URI(internalBaseUrl);
            URI rewritten = new URI(
                    internalBase.getScheme(),
                    null,
                    internalBase.getHost(),
                    internalBase.getPort(),
                    original.getPath(),
                    original.getQuery(),
                    original.getFragment());
            return rewritten.toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("No se pudo reescribir la URL: " + originalUrl, e);
        }
    }

}

