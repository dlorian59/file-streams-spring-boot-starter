# Existing-application consumer / Consumer существующего приложения

[← Integration EN](../../docs/0.1.0/integration-en.md) · [Подключение RU](../../docs/0.1.0/integration-ru.md) · [README](../../README.md)

`AttachmentController.java` and `application.yml` are the application code. Gradle/Maven resolve packaged local artifacts, not source projects. The test starts real Boot HTTP and Spring Security with its default firewall and synthetic Basic users; its bounded memory store is not S3.

`AttachmentController.java` и `application.yml` — код приложения. Gradle/Maven используют локальные артефакты, не исходные проекты. Тест запускает настоящий Boot HTTP/Spring Security со стандартным firewall и тестовыми Basic-пользователями; ограниченное хранилище теста — не S3.

[Development commands / Команды проверки](DEVELOPMENT.md)
