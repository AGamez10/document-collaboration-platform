package com.officeplatform.security.api;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.officeplatform.entity.ApiKeyEntity;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.security.model.ApiKeyPrincipal;
import com.officeplatform.util.Constants;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyFilter(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(Constants.API_KEY_HEADER);

        if (apiKey != null && !apiKey.isBlank()) {
            apiKeyRepository.findByApiKeyAndActiveTrue(apiKey)
                    .ifPresent(entity -> authenticate(entity, request));
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Deja la identidad en el principal, y no solo el proyecto.
     *
     * <p>Los consumidores con {@code X-Api-Key} mandan la cédula como parámetro de consulta. Antes
     * el principal se construía solo con el proyecto, y cada punto del código que necesitaba saber
     * quién llamaba tenía que acordarse de recibir ese parámetro y reenviarlo. Olvidarlo no fallaba
     * ruidosamente: la persona quedaba tratada como anónima, y así el propio autor de un archivo no
     * podía compartirlo y "Compartidos conmigo" devolvía una lista vacía. El mismo descuido ya
     * aparecio dos veces en sitios distintos, porque el problema no eran los sitios sino que la
     * identidad nunca llegaba al principal.
     *
     * <p>Resolverlo acá elimina la clase entera de error: quien recibe el principal ya tiene la
     * identidad, sin depender de que alguien la reenvie. Los controladores pueden seguir aceptando
     * el parámetro y {@code resolveUserId} le da prioridad, así que nada de lo existente cambia.
     */
    private void authenticate(ApiKeyEntity apiKeyEntity, HttpServletRequest request) {
        ApiKeyPrincipal principal = new ApiKeyPrincipal(
                apiKeyEntity.getId(), apiKeyEntity.getName(),
                queryParam(request, "userId"), queryParam(request, "userName"));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Lee un parámetro de la cadena de consulta sin tocar el cuerpo del request.
     *
     * <p>{@code getParameter} mezcla consulta y cuerpo, y sobre un multipart puede forzar el
     * consumo del cuerpo antes de que Spring lo resuelva: eso romperia toda subida de archivos.
     * La identidad siempre viaja en la URL, asi que alcanza con parsear la cadena de consulta.
     */
    private static String queryParam(HttpServletRequest request, String name) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (name.equals(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8))) {
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                return value.isBlank() ? null : value;
            }
        }
        return null;
    }

}
