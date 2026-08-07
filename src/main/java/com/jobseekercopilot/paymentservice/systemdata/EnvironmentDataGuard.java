package com.jobseekercopilot.paymentservice.systemdata;

import com.jobseekercopilot.paymentservice.config.EnvironmentDataProperties;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EnvironmentDataGuard {
    public static final String TOKEN_HEADER = "X-Environment-Data-Token";
    private static final int MINIMUM_TOKEN_BYTES = 32;

    private final EnvironmentDataProperties properties;
    private final Environment environment;

    public EnvironmentDataGuard(EnvironmentDataProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public void requireEnabled() {
        Set<String> activeProfiles = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (activeProfiles.isEmpty()) {
            activeProfiles = Set.of("default");
        }
        if (activeProfiles.contains("prod") || activeProfiles.contains("production")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Environment data management is forbidden in production");
        }
        Set<String> allowed = properties.getAllowedEnvironments().stream()
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!properties.isEnabled()
                || !properties.isIsolatedDatabase()
                || !validConfiguredToken()
                || activeProfiles.stream().noneMatch(allowed::contains)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Environment data management is disabled");
        }
    }

    public boolean hasValidToken(String suppliedToken) {
        if (!validConfiguredToken() || suppliedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                properties.getToken().getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8));
    }

    public String activeEnvironment() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "default" : String.join(",", profiles);
    }

    private boolean validConfiguredToken() {
        return properties.getToken() != null
                && !properties.getToken().isBlank()
                && properties.getToken().getBytes(StandardCharsets.UTF_8).length
                >= MINIMUM_TOKEN_BYTES;
    }
}
