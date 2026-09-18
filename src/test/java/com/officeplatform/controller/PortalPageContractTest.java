package com.officeplatform.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.officeplatform.dto.response.ApiResponse;
import com.officeplatform.exception.GlobalExceptionHandler;
import com.officeplatform.exception.InvalidCredentialsException;

/**
 * Lo que el portal promete antes de que alguien pueda usarlo.
 *
 * <p>La página salió a producción sin estilos ni scripts: fondo negro, Times New Roman y un
 * formulario que al enviarse respondía 403. Las dos cosas eran el mismo descuido. El HTML pedía
 * sus assets con rutas relativas, así que servido en {@code /portal} —sin barra final— el
 * navegador los buscaba en la raíz, fuera del directorio permitido; sin el script, el formulario
 * hacía un envío nativo del navegador que la seguridad rechazaba.
 *
 * <p>Ninguna prueba de servicio podía ver eso: no es lógica, es el contrato entre una página y su
 * servidor. Estos casos lo fijan donde se rompió.
 */
class PortalPageContractTest {

    private String pagina(String classpathResource) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpathResource)) {
            assertThat(in).as("No existe el recurso %s", classpathResource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("the portal asks for its assets by absolute path")
    void thePortalLinksItsAssetsAbsolutely() throws Exception {
        String html = pagina("static/portal/index.html");

        // Con href="portal.css", entrar a /portal pide /portal.css: una ruta que no existe y que
        // la seguridad rechaza con 403, y la página se dibuja desnuda.
        assertThat(html).contains("/portal/portal.css").contains("/portal/portal.js");
        assertThat(html).doesNotContain("href=\"portal.css").doesNotContain("src=\"portal.js");
    }

    @Test
    @DisplayName("the admin panel does not repeat the same mistake")
    void theAdminPanelLinksItsAssetsAbsolutely() throws Exception {
        String html = pagina("static/admin/index.html");

        assertThat(html).contains("/admin/admin.css").contains("/admin/admin.js");
        assertThat(html).doesNotContain("href=\"admin.css").doesNotContain("src=\"admin.js");
    }

    @Test
    @DisplayName("a failed login answers 401, which is not the same as 403")
    void aFailedLoginIsUnauthorizedAndNotForbidden() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/portal/auth/login");

        ResponseEntity<ApiResponse<Void>> respuesta = new GlobalExceptionHandler()
                .handleInvalidCredentialsException(
                        new InvalidCredentialsException("Cédula o contraseña incorrecta."), request);

        // 403 significa "sé quién sos y no te alcanza"; acá el servidor no sabe quién es nadie.
        // Devolver 403 hacía que el fallo se confundiera con una ruta mal permitida en la
        // configuración de seguridad, y se buscara el problema donde no estaba.
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ApiResponse<Void> cuerpo = respuesta.getBody();

        // El mensaje es lo que el portal muestra en pantalla: si cambia acá sin querer, el usuario
        // se queda con un error genérico que no le dice qué corregir.
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.getMessage()).isEqualTo("Cédula o contraseña incorrecta.");
        assertThat(cuerpo.getSuccess()).isFalse();
    }
}
