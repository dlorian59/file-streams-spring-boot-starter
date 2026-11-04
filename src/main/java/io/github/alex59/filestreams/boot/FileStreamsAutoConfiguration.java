package io.github.alex59.filestreams.boot;

import io.github.alex59.filestreams.ObjectStreams;
import io.github.alex59.filestreams.StreamLimits;
import io.github.alex59.filestreams.s3.S3Clients;
import io.github.alex59.filestreams.s3.S3ObjectStreams;
import io.github.alex59.filestreams.webflux.ObjectStreamHandler;
import java.util.Map;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

/** Adapts the existing bounded streaming engine to application-owned Spring configuration. */
@AutoConfiguration
@ConditionalOnProperty(prefix = "file-streams", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(FileStreamsProperties.class)
public class FileStreamsAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    StreamLimits fileStreamLimits(FileStreamsProperties properties) {
        var limits = properties.limits();
        return new StreamLimits(limits.maxObjectSize().toBytes(), (int) limits.maxBufferSize().toBytes());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(ObjectStreams.class)
    static class S3Configuration {
        @Bean
        ObjectStreams fileObjectStreams(S3AsyncClient client, StreamLimits limits, FileStreamsProperties properties) {
            Assert.hasText(properties.s3().bucket(), "file-streams.s3.bucket is required");
            return new S3ObjectStreams(client, properties.s3().bucket(), limits,
                    properties.limits().maxConcurrentTransfers());
        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnMissingBean(S3AsyncClient.class)
        static class ClientConfiguration {
            @Bean(destroyMethod = "close")
            @ConditionalOnMissingBean(AwsCredentialsProvider.class)
            DefaultCredentialsProvider fileStreamsCredentialsProvider() {
                return DefaultCredentialsProvider.builder().build();
            }

            @Bean(destroyMethod = "close")
            S3AsyncClient fileStreamsS3Client(AwsCredentialsProvider credentials, FileStreamsProperties properties) {
                Assert.hasText(properties.s3().region(), "file-streams.s3.region is required");
                return S3Clients.bounded(properties.s3().endpoint(), Region.of(properties.s3().region()), credentials);
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnProperty(prefix = "file-streams.http", name = "enabled", havingValue = "true")
    static class HttpConfiguration {
        @Bean
        @ConditionalOnMissingBean
        ObjectStreamHandler fileStreamsHttpHandler(ObjectStreams objects, StreamLimits limits) {
            return new ObjectStreamHandler(objects, limits);
        }

        @Bean
        @ConditionalOnMissingBean(name = "fileStreamsHandlerMapping")
        SimpleUrlHandlerMapping fileStreamsHandlerMapping(ObjectStreamHandler handler) {
            return new SimpleUrlHandlerMapping(Map.of("/objects/**", handler), -1);
        }
    }
}
