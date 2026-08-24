package com.officeplatform.security.api;

import java.io.IOException;
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
            apiKeyRepository.findByApiKeyAndActiveTrue(apiKey).ifPresent(this::authenticate);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(ApiKeyEntity apiKeyEntity) {
        ApiKeyPrincipal principal = new ApiKeyPrincipal(apiKeyEntity.getId(), apiKeyEntity.getName());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

}
