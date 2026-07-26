package com.jobseekercopilot.paymentservice.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobseekercopilot.paymentservice.systemdata.EnvironmentDataGuard;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

class EnvironmentDataGuardTest {
    @Test
    void rejectsEnabledFixtureMutationUnlessDatabaseIsExplicitlyIsolated() {
        EnvironmentDataProperties properties = new EnvironmentDataProperties();
        properties.setEnabled(true);
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
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        EnvironmentDataGuard guard = new EnvironmentDataGuard(properties, environment);

        assertThatCode(guard::requireEnabled).doesNotThrowAnyException();
    }

    @Test
    void productionProfileCannotEnableFixtureMutation() {
        EnvironmentDataProperties properties = new EnvironmentDataProperties();
        properties.setEnabled(true);
        properties.setIsolatedDatabase(true);
        properties.setAllowedEnvironments(java.util.List.of("production"));
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");

        EnvironmentDataGuard guard = new EnvironmentDataGuard(properties, environment);

        assertThatThrownBy(guard::requireEnabled)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("forbidden in production");
    }
}
