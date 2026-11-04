package configuration;

import io.github.alex59.filestreams.ObjectStreams;
import io.github.alex59.filestreams.StreamLimits;
import io.github.alex59.filestreams.s3.S3Clients;
import io.github.alex59.filestreams.webflux.ObjectStreamHandler;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

// EN: These configurations are outside the demo's component-scan package; import only the example you need.
// RU: Эти конфигурации вне component scan demo; импортируйте только нужный пример.
public final class FileStreamsOverrides {
    private FileStreamsOverrides() {}

    @Configuration(proxyBeanMethods = false)
    public static class ApplicationLimits {
        // EN: Replacing StreamLimits changes upload limits; it does not cap an existing object's download size.
        // RU: Замена StreamLimits меняет лимиты upload, но не размер скачивания существующего объекта.
        @Bean
        StreamLimits applicationLimits() {
            return new StreamLimits(64L * 1024 * 1024, 64 * 1024);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ApplicationCredentials {
        // EN: The named local AWS profile must exist; Spring closes this application-owned provider at shutdown.
        // RU: Именованный AWS-профиль должен существовать; Spring закрывает provider приложения при остановке.
        @Bean(destroyMethod = "close")
        ProfileCredentialsProvider applicationCredentials() {
            return ProfileCredentialsProvider.builder().profileName("documents").build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ApplicationClient {
        // EN: Import with ApplicationCredentials or another provider bean: a supplied client disables default credentials wiring.
        // RU: Импортируйте вместе с ApplicationCredentials либо иным provider: свой клиент отключает default credentials wiring.
        @Bean(destroyMethod = "close")
        S3AsyncClient applicationClient(AwsCredentialsProvider credentials) {
            return S3Clients.bounded(null, Region.EU_WEST_1, credentials);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ApplicationHandler {
        // EN: Reuse the HTTP engine with the application's storage and limits; keep authentication in host filters.
        // RU: Используем HTTP engine с хранилищем и лимитами приложения; аутентификация остаётся в host filters.
        @Bean
        ObjectStreamHandler applicationHandler(ObjectStreams objects, StreamLimits limits) {
            return new ObjectStreamHandler(objects, limits);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ApplicationRouteOrder {
        // EN: Backoff for this extension uses the exact bean name; the underlying handler still expects /objects/**.
        // RU: Здесь backoff определяется точным именем bean; сам handler по-прежнему ожидает /objects/**.
        @Bean(name = "fileStreamsHandlerMapping")
        SimpleUrlHandlerMapping applicationRouteOrder(ObjectStreamHandler handler) {
            return new SimpleUrlHandlerMapping(Map.of("/objects/**", handler), -2);
        }
    }
}
