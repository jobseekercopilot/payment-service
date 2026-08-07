package com.jobseekercopilot.paymentservice.systemdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.paymentservice.security.PaymentIdentityError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class EnvironmentDataAuthenticationFilter extends OncePerRequestFilter {
    private static final String PROTECTED_PATH = "/internal/system-data";

    private final EnvironmentDataGuard guard;
    private final ObjectMapper objectMapper;

    public EnvironmentDataAuthenticationFilter(
            EnvironmentDataGuard guard,
            ObjectMapper objectMapper) {
        this.guard = guard;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals(PROTECTED_PATH) || path.startsWith(PROTECTED_PATH + "/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        List<String> tokens =
                Collections.list(request.getHeaders(EnvironmentDataGuard.TOKEN_HEADER));
        if (tokens.size() != 1 || !guard.hasValidToken(tokens.get(0))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                    response.getOutputStream(),
                    new PaymentIdentityError(
                            "ENVIRONMENT_DATA_UNAUTHORIZED",
                            "Environment data credentials are invalid."));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
