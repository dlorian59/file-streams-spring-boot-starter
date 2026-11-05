package com.example.attachments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// EN: In an existing application, keep its existing entry point and security configuration.
// RU: В существующем приложении сохраните его entry point и конфигурацию безопасности.
@SpringBootApplication
public class AttachmentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AttachmentApplication.class, args);
    }
}
