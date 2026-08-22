package com.jobseekercopilot.paymentservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobseekercopilot.paymentservice.systemdata.EnvironmentDataGuard;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

class EnvironmentDataGuardTest {
    private static final String TOKEN =
            "environment-data-test-token-000000000001";

    @Test
    void rejectsEnabledFixtureMutationUnlessDatabaseIsExplicitlyIsolated() {
        EnvironmentDataProperties properties = new EnvironmentDataProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        EnvironmentDataGuard guard = new EnvironmentDataGuard(properties, environment);

        assertThatThrownBy(guard::requireEnabled)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Environment data management is disabled");
    }

    @Test
    void permitsFixtureMutationOnlyForEnabledIsolatedAllowedProfile() {
        EnvironmentDataProperties properties = new EnvironmentDataProperties();
        properties.setEnabled(true);
        properties.setIsolatedDatabase(true);
        properties.setToken(TOKEN);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        EnvironmentDataGuard guard = new EnvironmentDataGuard(properties, environment);

        assertThatCode(guard::requireEnabled).doesNotThrowAnyException();
        assertThat(guard.hasValidToken(TOKEN)).isTrue();
        assertThat(guard.hasValidToken("wrong-environment-data-token-0000001")).isFalse();
    }

    @Test
    void rejectsEnabledFixtureMutationWithAShortCredential() {
        EnvironmentDataProperties properties = new EnvironmentDataProperties();
        properties.setEnabled(true);
        properties.setIsolatedDatabase(true);
        properties.setToken("too-short");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        EnvironmentDataGuard guard = new EnvironmentDataGuard(properties, environment);

        assertThatThrownBy(guard::requireEnabled)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Environment data management is disabled");
        assertThat(guard.hasValidToken("too-short")).isFalse();
    }

    @Test
    void productionProfileCannotEnableFixtureMutation() {
        EnvironmentDataProperties properties = new EnvironmentDataProperties();
        properties.setEnabled(true);
        properties.setIsolatedDatabase(true);
        properties.setAllowedEnvironments(java.util.List.of("production"));
        properties.setToken(TOKEN);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");

        EnvironmentDataGuard guard = new EnvironmentDataGuard(properties, environment);

        assertThatThrownBy(guard::requireEnabled)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("forbidden in production");
    }
}
