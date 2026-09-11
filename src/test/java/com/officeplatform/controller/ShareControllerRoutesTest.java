package com.officeplatform.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The routes the sharing endpoints actually expose.
 *
 * <p>The trash of shared resources silently came back empty for days. The controller hangs off
 * {@code /api} and mapped {@code /trashed-with-me}, so the real path was {@code /api/trashed-with-me};
 * the widget asked for {@code /api/share/trashed-with-me}. Spring matched that by prefix against the
 * {@code DELETE /share/{permissionId}} mapping and answered "method not supported", and the
 * {@code allSettled} in the widget swallowed the failure and rendered an empty trash. Nothing in the
 * interface said anything was wrong.
 *
 * <p>A path is a contract, and a contract nobody checks drifts. These cases read the annotations so
 * a rename cannot quietly break the consumers again.
 */
class ShareControllerRoutesTest {

    private List<String> pathsOf(String methodName) throws Exception {
        Method method = Arrays.stream(ShareController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No existe el método " + methodName));
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertThat(mapping).as("El método %s debe estar anotado con @GetMapping", methodName).isNotNull();
        return Arrays.asList(mapping.value());
    }

    private String base() {
        return ShareController.class.getAnnotation(RequestMapping.class).value()[0];
    }

    @Test
    @DisplayName("the shared trash answers on the path the widget asks for")
    void exposesThePathTheWidgetUses() throws Exception {
        List<String> paths = pathsOf("trashedWithMe");

        // Esta es la ruta canonica, la que el widget pide hoy.
        assertThat(base() + paths.get(0)).isEqualTo("/api/trashed-with-me");
    }

    @Test
    @DisplayName("the old path keeps answering, so no consumer breaks on the fix")
    void keepsTheOldPathAlive() throws Exception {
        List<String> completas = pathsOf("trashedWithMe").stream().map(p -> base() + p).toList();

        // Cualquiera que ya haya escrito /api/share/trashed-with-me sigue funcionando: arreglar
        // un desfase de rutas no puede convertirse en romper a quien se adapto al desfase.
        assertThat(completas).contains("/api/trashed-with-me", "/api/share/trashed-with-me");
    }

    @Test
    @DisplayName("the live listing and the trash listing are different paths")
    void doesNotCollideWithTheLiveListing() throws Exception {
        List<String> papelera = pathsOf("trashedWithMe");
        List<String> vivos = pathsOf("sharedWithMe");

        // Si colisionaran, una de las dos vistas mostraria el contenido de la otra.
        assertThat(papelera).doesNotContainAnyElementsOf(vivos);
    }

}
