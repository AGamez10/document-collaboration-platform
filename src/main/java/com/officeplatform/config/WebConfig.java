package com.officeplatform.config;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // La barra final no es un detalle estético: decide contra qué resuelve el navegador
        // cualquier ruta relativa de la página. Servida en /portal, una hoja de estilos escrita
        // como "portal.css" se pide a /portal.css —fuera del directorio y fuera de lo permitido—
        // y la página aparece sin estilos ni scripts. Redirigir primero deja una sola URL
        // canónica y cierra esa clase de error de raíz; los enlaces absolutos del HTML son el
        // segundo cinturón.
        registry.addRedirectViewController("/admin", "/admin/");
        registry.addRedirectViewController("/portal", "/portal/");

        // Spring solo resuelve index.html automáticamente en la raíz, no en subdirectorios:
        // sin esto, /portal/ responde NoResourceFoundException y el usuario tendría que
        // escribir /portal/index.html a mano.
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
        registry.addViewController("/portal/").setViewName("forward:/portal/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Disable aggressive caching on admin static assets so UI updates are immediately reflected
        registry.addResourceHandler("/admin/**")
                .addResourceLocations("classpath:/static/admin/")
                .setCacheControl(CacheControl.noCache().mustRevalidate());

        registry.addResourceHandler("/portal/**")
                .addResourceLocations("classpath:/static/portal/")
                .setCacheControl(CacheControl.noCache().mustRevalidate());
    }

}
