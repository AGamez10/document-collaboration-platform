package com.officeplatform.security.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.officeplatform.entity.ApiKeyEntity;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.security.model.ApiKeyPrincipal;

import jakarta.servlet.FilterChain;

/**
 * The identity a caller authenticated with {@code X-Api-Key} carries.
 *
 * <p>Those consumers send their cédula as a query parameter, and the principal used to be built
 * from the project alone. Every place that needed to know who was calling had to remember to accept
 * that parameter and forward it, and forgetting never failed loudly: the person was simply treated
 * as anonymous. That is how the author of a file ended up unable to share it and how
 * "Compartidos conmigo" returned an empty list to someone who had resources shared with them — the
 * same slip in two separate places, because the sites were never the problem. Resolving the
 * identity here removes the whole class of error, so these cases pin it at the source.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiKeyIdentityTest {

    private static final String KEY = "opk_prueba";

    @Mock private ApiKeyRepository apiKeyRepository;

    private ApiKeyFilter filter;

    @BeforeEach
    void setUp() {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(7L);
        entity.setApiKey(KEY);
        entity.setName("DisenoDesarrollo");
        entity.setActive(true);
        lenient().when(apiKeyRepository.findByApiKeyAndActiveTrue(anyString()))
                .thenReturn(Optional.of(entity));

        filter = new ApiKeyFilter(apiKeyRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** Corre el filtro sobre una petición y devuelve el principal que dejó autenticado. */
    private ApiKeyPrincipal run(String queryString) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files");
        request.addHeader("X-Api-Key", KEY);
        if (queryString != null) {
            request.setQueryString(queryString);
        }
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : (ApiKeyPrincipal) auth.getPrincipal();
    }

    @Test
    @DisplayName("the cédula sent as a query parameter reaches the principal")
    void carriesTheIdentityFromTheQueryString() throws Exception {
        ApiKeyPrincipal principal = run("userId=1004356866&userName=Ana");

        assertThat(principal.getApiKeyId()).isEqualTo(7L);
        // Antes esto era null y toda la vía X-Api-Key se veía anónima.
        assertThat(principal.getUserId()).isEqualTo("1004356866");
        assertThat(principal.getUserName()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("resolveUserId no longer answers null for an API-key caller")
    void resolvesTheIdentityWithoutForwardingIt() throws Exception {
        ApiKeyPrincipal principal = run("userId=1004356866&userName=Ana");

        // Este es el punto: los sitios que llaman resolveUserId(null) ahora aciertan solos, sin
        // depender de que alguien se acuerde de reenviar el parámetro.
        assertThat(principal.resolveUserId(null)).isEqualTo("1004356866");
        assertThat(principal.resolveUserName(null)).isEqualTo("Ana");
    }

    @Test
    @DisplayName("an explicit value still wins over the one carried by the request")
    void anExplicitValueStillWins() throws Exception {
        ApiKeyPrincipal principal = run("userId=1004356866&userName=Ana");

        assertThat(principal.resolveUserId("otro")).isEqualTo("otro");
    }

    @Test
    @DisplayName("values are URL-decoded, so a name with spaces or accents survives")
    void decodesTheValues() throws Exception {
        ApiKeyPrincipal principal = run("userId=1004356866&userName=Ana%20P%C3%A9rez");

        assertThat(principal.getUserName()).isEqualTo("Ana Pérez");
    }

    @Test
    @DisplayName("a request with no identity still authenticates the project")
    void keepsWorkingWithoutIdentity() throws Exception {
        ApiKeyPrincipal principal = run(null);

        // Retrocompatible: un consumidor que nunca mandó cédula sigue funcionando igual.
        assertThat(principal.getApiKeyId()).isEqualTo(7L);
        assertThat(principal.getUserId()).isNull();
    }

    @Test
    @DisplayName("a blank identity is treated as absent instead of as an empty user")
    void treatsBlankAsAbsent() throws Exception {
        ApiKeyPrincipal principal = run("userId=&userName=");

        assertThat(principal.getUserId()).isNull();
        assertThat(principal.getUserName()).isNull();
    }

    @Test
    @DisplayName("another parameter ending in the same word is not mistaken for the identity")
    void doesNotMatchASuffixOfAnotherParameter() throws Exception {
        // 'targetUserId' termina en 'userId': una comparación descuidada tomaría su valor.
        ApiKeyPrincipal principal = run("targetUserId=999&scope=shared");

        assertThat(principal.getUserId()).isNull();
    }

    @Test
    @DisplayName("the body of the request is never touched, so uploads keep working")
    void neverReadsTheBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/files/upload");
        request.addHeader("X-Api-Key", KEY);
        request.setQueryString("userId=1004356866");
        request.setContentType("multipart/form-data; boundary=abc");
        request.setContent("cuerpo intacto".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);

        // Leer el cuerpo acá rompería toda subida de archivos: Spring ya no encontraría el
        // multipart. Sigue disponible entero para quien viene después en la cadena.
        assertThat(new String(request.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("cuerpo intacto");
        verify(chain).doFilter(request, response);

        ApiKeyPrincipal principal = (ApiKeyPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        assertThat(principal.getUserId()).isEqualTo("1004356866");
    }

}
