package com.likehang.alphaforge.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

@ExtendWith(OutputCaptureExtension.class)
class ProductionConfigurationTest {

    private static final String TEST_PASSWORD = "only-a-test-value-never-a-real-password";

    @Test
    void loadsProductionSettingsWithoutStartingDatabaseOrDevelopmentSeeder() {
        try (ConfigurableApplicationContext context = application(validSettings()).run()) {
            var environment = context.getEnvironment();
            assertThat(environment.getActiveProfiles()).containsExactly("prod");
            assertThat(environment.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://db.example.test:5432/alphaforge");
            assertThat(environment.getProperty("spring.datasource.password")).isEqualTo(TEST_PASSWORD);

            Map<String, String> driverProperties = Binder.get(environment)
                    .bind("spring.datasource.hikari.data-source-properties", Bindable.mapOf(String.class, String.class))
                    .orElseThrow(IllegalStateException::new);
            assertThat(driverProperties)
                    .containsEntry("sslmode", "verify-full")
                    .containsEntry("sslfactory", "org.postgresql.ssl.DefaultJavaSSLFactory");
            assertThat(context.getBeansOfType(LocalDevDataSeeder.class)).isEmpty();
            assertThat(environment.getProperty("alphaforge.dev.seed-data.enabled", Boolean.class)).isFalse();
            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
            assertThat(environment.getProperty("server.servlet.session.cookie.secure", Boolean.class)).isTrue();
            assertThat(environment.getProperty("server.servlet.session.cookie.http-only", Boolean.class)).isTrue();
            assertThat(environment.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
            assertThat(environment.getProperty("spring.web.error.include-stacktrace")).isEqualTo("never");
            assertThat(environment.getProperty("spring.mvc.log-request-details", Boolean.class)).isFalse();
            assertThat(environment.getProperty("alphaforge.cors.allowed-origins")).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_URL", "DB_USERNAME", "DB_PASSWORD", "TRUSTED_PROXY_SECRET"})
    void rejectsMissingDatabaseSettingBeforeContextRefresh(String variable, CapturedOutput output) {
        var settings = validSettings();
        settings.remove(variable);

        Throwable failure = catchThrowable(() -> application(settings).run());

        assertThat(failure).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing production configuration")
                .hasMessageContaining(variable);
        assertNoCredentialsInFailure(failure, output);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_URL", "DB_USERNAME", "DB_PASSWORD", "TRUSTED_PROXY_SECRET"})
    void rejectsBlankDatabaseSetting(String variable, CapturedOutput output) {
        var settings = validSettings();
        settings.put(variable, "  ");

        Throwable failure = catchThrowable(() -> application(settings).run());

        assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessageContaining(variable);
        assertNoCredentialsInFailure(failure, output);
    }

    @Test
    void reportsAllMissingVariableNamesTogether(CapturedOutput output) {
        Throwable failure = catchThrowable(() -> application(Map.of("spring.profiles.active", "prod")).run());

        assertThat(failure).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL, DB_USERNAME, DB_PASSWORD");
        assertNoCredentialsInFailure(failure, output);
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod,local", "prod,test", "local,prod", "test,prod"})
    void rejectsProductionMixedWithDevelopmentOrTest(String profiles, CapturedOutput output) {
        var settings = validSettings();
        settings.put("spring.profiles.active", profiles);

        Throwable failure = catchThrowable(() -> application(settings).run());

        assertThat(failure).isInstanceOf(IllegalStateException.class)
                .hasMessage("The prod profile cannot be combined with local or test.");
        assertNoCredentialsInFailure(failure, output);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mysql://db.example.test/alphaforge",
            "jdbc:postgresql:alphaforge",
            "jdbc:postgresql://db.example.test/",
            "jdbc:postgresql://db.example.test/alphaforge?sslmode=disable",
            "jdbc:postgresql://db.example.test/alphaforge?password=only-a-test-value-never-a-real-password",
            "jdbc:postgresql://app:only-a-test-value-never-a-real-password@db.example.test/alphaforge",
            "jdbc:postgresql://db.example.test/alphaforge#fragment",
            "jdbc:postgresql://not a hostname/alphaforge"
    })
    void rejectsUnsafeOrInvalidUrlWithoutEchoingIt(String url, CapturedOutput output) {
        var settings = validSettings();
        settings.put("DB_URL", url);

        Throwable failure = catchThrowable(() -> application(settings).run());

        assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessageContaining("DB_URL must use");
        assertThat(failure.getMessage()).doesNotContain(url);
        assertNoCredentialsInFailure(failure, output);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test"})
    void doesNotRequireProductionCredentialsForLocalOrTest(String profile) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profile);

        assertThatCode(() -> new ProductionEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication(ProbeConfiguration.class)))
                .doesNotThrowAnyException();
    }

    private Map<String, Object> validSettings() {
        var settings = new HashMap<String, Object>();
        settings.put("spring.profiles.active", "prod");
        settings.put("DB_URL", "jdbc:postgresql://db.example.test:5432/alphaforge");
        settings.put("DB_USERNAME", "production_test_account");
        settings.put("DB_PASSWORD", TEST_PASSWORD);
        settings.put("TRUSTED_PROXY_SECRET", "test-proxy-secret-with-at-least-32-characters");
        return settings;
    }

    private SpringApplication application(Map<String, Object> settings) {
        // Never allow the test process to read a developer's real credentials or profile overrides.
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("production-test-settings", settings));

        SpringApplication application = new SpringApplication(ProbeConfiguration.class);
        application.setEnvironment(environment);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.setDefaultProperties(Map.of(
                "spring.config.location", "classpath:/application.yaml",
                "logging.level.root", "OFF"
        ));
        return application;
    }

    private void assertNoCredentialsInFailure(Throwable failure, CapturedOutput output) {
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        assertThat(trace.toString() + output.getAll())
                .doesNotContain(TEST_PASSWORD, "production_test_account", "db.example.test");
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import(LocalDevDataSeeder.class)
    static class ProbeConfiguration {
    }
}
