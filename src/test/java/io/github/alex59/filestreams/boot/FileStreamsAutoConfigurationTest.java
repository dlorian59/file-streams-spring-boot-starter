package io.github.alex59.filestreams.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import io.github.alex59.filestreams.ObjectStreams;
import io.github.alex59.filestreams.StreamLimits;
import io.github.alex59.filestreams.s3.S3Clients;
import io.github.alex59.filestreams.webflux.ObjectStreamHandler;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

class FileStreamsAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FileStreamsAutoConfiguration.class));

    @Test
    void importsAreDiscoverableAndNothingIsCreatedWithoutExplicitEnablement() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(FileStreamsAutoConfiguration.class.getName());
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(ObjectStreams.class);
            assertThat(context).doesNotHaveBean(S3AsyncClient.class);
            assertThat(context).doesNotHaveBean(AwsCredentialsProvider.class);
        });
    }

    @Test
    void configuredClientUsesExistingBoundedEngineAndClosesOnShutdown() {
        var client = mock(S3AsyncClient.class);
        try (var factory = mockStatic(S3Clients.class)) {
            factory.when(() -> S3Clients.bounded(isNull(), any(Region.class), any(AwsCredentialsProvider.class)))
                    .thenReturn(client);
            runner.withPropertyValues("file-streams.enabled=true", "file-streams.s3.bucket=files",
                    "file-streams.s3.region=eu-west-1", "file-streams.limits.max-object-size=64MB",
                    "file-streams.limits.max-buffer-size=64KB", "file-streams.limits.max-concurrent-transfers=3")
                    .run(context -> {
                        assertThat(context).hasSingleBean(ObjectStreams.class);
                        assertThat(context).hasSingleBean(DefaultCredentialsProvider.class);
                        assertThat(context.getBean(StreamLimits.class)).isEqualTo(new StreamLimits(64L << 20, 64 << 10));
                        factory.verify(() -> S3Clients.bounded(null, Region.EU_WEST_1,
                                context.getBean(AwsCredentialsProvider.class)));
                        verify(client, never()).close();
                    });
            verify(client).close();
        }
    }

    @Test
    void customStorageAndLimitsAvoidCreatingUnusedAwsResources() {
        var objects = mock(ObjectStreams.class);
        var limits = new StreamLimits(123, 45);
        runner.withPropertyValues("file-streams.enabled=true")
                .withBean(ObjectStreams.class, () -> objects)
                .withBean(StreamLimits.class, () -> limits)
                .run(context -> {
                    assertThat(context).hasSingleBean(ObjectStreams.class).hasSingleBean(StreamLimits.class);
                    assertThat(context.getBean(ObjectStreams.class)).isSameAs(objects);
                    assertThat(context.getBean(StreamLimits.class)).isSameAs(limits);
                    assertThat(context).doesNotHaveBean(S3AsyncClient.class).doesNotHaveBean(AwsCredentialsProvider.class);
                });
    }

    @Test
    void suppliedClientKeepsItsOwnLifecycleAndNeedsNoDefaultCredentialsProvider() {
        var client = ExternalClientConfiguration.CLIENT;
        runner.withUserConfiguration(ExternalClientConfiguration.class)
                .withPropertyValues("file-streams.enabled=true", "file-streams.s3.bucket=files")
                .run(context -> {
                    assertThat(context.getBean(S3AsyncClient.class)).isSameAs(client);
                    assertThat(context).hasSingleBean(ObjectStreams.class).doesNotHaveBean(AwsCredentialsProvider.class);
                });
        verify(client, never()).close();
    }

    @Test
    void invalidSettingsFailAtStartupWithActionableMessages() {
        for (String value : new String[] {"file-streams.limits.max-buffer-size=0B",
                "file-streams.limits.max-concurrent-transfers=0", "file-streams.s3.endpoint=ftp://localhost"}) {
            runner.withPropertyValues("file-streams.enabled=true", value)
                    .withBean(ObjectStreams.class, () -> mock(ObjectStreams.class))
                    .run(context -> assertThat(context).hasFailed());
        }
        runner.withPropertyValues("file-streams.enabled=true")
                .withUserConfiguration(ExternalClientConfiguration.class)
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("file-streams.s3.bucket is required"));
    }

    @Test
    void httpMappingIsOptInAndHandlerBacksOff() {
        var web = new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FileStreamsAutoConfiguration.class))
                .withPropertyValues("file-streams.enabled=true")
                .withBean(ObjectStreams.class, () -> mock(ObjectStreams.class));
        web.run(context -> assertThat(context).hasSingleBean(ObjectStreamHandler.class)
                .doesNotHaveBean("fileStreamsHandlerMapping"));
        web.withPropertyValues("file-streams.http.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(ObjectStreamHandler.class).hasBean("fileStreamsHandlerMapping");
        });
        var supplied = new ObjectStreamHandler(mock(ObjectStreams.class), StreamLimits.defaults());
        web.withPropertyValues("file-streams.http.enabled=true")
                .withBean(ObjectStreamHandler.class, () -> supplied)
                .run(context -> assertThat(context.getBean(ObjectStreamHandler.class)).isSameAs(supplied));
    }

    @Test
    void namedProfileBindsAndExplicitBoundsStillOverrideIt() {
        var configured = runner.withPropertyValues("file-streams.enabled=true", "file-streams.limits.profile=attachments")
                .withBean(ObjectStreams.class, () -> mock(ObjectStreams.class));
        configured.run(context -> assertThat(context.getBean(StreamLimits.class))
                .isEqualTo(io.github.alex59.filestreams.StreamProfile.ATTACHMENTS.limits()));
        configured.withPropertyValues("file-streams.limits.max-object-size=5MB").run(context ->
                assertThat(context.getBean(StreamLimits.class).maxBytes()).isEqualTo(5L << 20));
        configured.withPropertyValues("file-streams.limits.profile=not-a-profile")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class ExternalClientConfiguration {
        static final S3AsyncClient CLIENT = mock(S3AsyncClient.class);
        @Bean(destroyMethod = "")
        S3AsyncClient externalClient() { return CLIENT; }
    }
}
