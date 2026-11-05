package com.example.attachments;

import io.github.alex59.filestreams.webflux.ObjectStreamHandler;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// EN: Account IDs here are stable URL-safe IDs supplied by the application's authentication layer.
// RU: Account ID здесь — стабильный URL-safe ID из аутентификации приложения.
@RestController
public final class AttachmentController {
    private final ObjectStreamHandler files;

    public AttachmentController(ObjectStreamHandler files) {
        this.files = files;
    }

    @RequestMapping(path = "/attachments/{key}", method = {RequestMethod.PUT, RequestMethod.GET})
    public Mono<Void> transfer(@PathVariable("key") String key, Principal account, ServerWebExchange exchange) {
        // EN: Authorize before reading or subscribing to the body; never trust an account header or query.
        // RU: Проверяем права до чтения и подписки на тело; account из header/query не считается аутентификацией.
        if (account == null) return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!account.getName().matches("[A-Za-z0-9_-]+") || !key.startsWith(account.getName() + ":")) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN));
        }
        // EN: Reuse streaming, length checks, ranges, errors and cleanup; business code does no buffering.
        // RU: Переиспользуем стрим, проверку длины, ranges, ошибки и cleanup; бизнес-код ничего не буферизует.
        return files.handle(exchange, key);
    }
}
