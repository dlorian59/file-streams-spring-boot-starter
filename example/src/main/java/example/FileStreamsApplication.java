package example;

import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

// EN: The starter supplies the streaming endpoint; this application supplies its browser access policy.
// RU: Starter подключает потоковый endpoint; приложение задаёт политику доступа браузера.
@SpringBootApplication
public class FileStreamsApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileStreamsApplication.class, args);
    }

    @Bean
    RouterFunction<ServerResponse> applicationStatus() {
        // EN: Ordinary application routes coexist with the starter; this reports application readiness, not storage health.
        // RU: Обычные маршруты работают рядом со starter; здесь готовность приложения, а не проверка хранилища.
        return RouterFunctions.route(GET("/example/status"),
                request -> ServerResponse.ok().bodyValue(Map.of("application", "ready")));
    }

    @Bean
    CorsWebFilter exampleCorsPolicy() {
        var cors = new CorsConfiguration();
        // EN: Limit local development access to the paired React example; configure real authentication separately.
        // RU: Разрешаем локальный React-пример; настоящая аутентификация настраивается отдельно.
        cors.setAllowedOrigins(List.of("https://localhost:5173", "https://127.0.0.1:5173"));
        cors.setAllowedMethods(List.of("GET", "PUT", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-Upload-Length", "Range"));
        cors.setExposedHeaders(List.of("Content-Type", "Content-Length", "ETag", "Accept-Ranges", "Content-Range", "Content-Encoding"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/objects/**", cors);
        return new CorsWebFilter(source);
    }
}
