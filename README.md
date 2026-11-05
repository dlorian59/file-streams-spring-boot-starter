# file-streams-spring-boot-starter

<details name="readme-language">
<summary><picture><source media="(prefers-color-scheme: dark)" srcset="assets/lang-en-dark.svg"><img src="assets/lang-en-light.svg" width="42" height="26" alt="EN"></picture></summary>

## Stream files with Spring Boot

Connect raw HTTP uploads and downloads to the existing `file-streams` engine with a dependency and configuration. The starter provides bounded S3 transfer, typed size limits, the AWS default credentials chain and an optional WebFlux endpoint. It leaves authentication, authorization, object-key policy and CORS with the application.

- **S3 wiring:** reuses `S3Clients` and `S3ObjectStreams`; one external byte stream, bounded internal AWS multipart.
- **HTTP, opt-in:** PUT/GET `/objects/{encodedKey}` through the existing `ObjectStreamHandler`.
- **Lifecycle:** closes clients/credentials it creates with the application context; custom beans retain their declared Spring lifecycle.
- **Overrides:** supply `ObjectStreams`, `StreamLimits`, `S3AsyncClient`, `AwsCredentialsProvider` or `ObjectStreamHandler` as needed.
- **Examples:** [Boot application](example/src/main/java/example/FileStreamsApplication.java), [configuration](example/src/main/resources/application.yml), [run script](scripts/run-example.sh). Comments pair English and Russian.

```yaml
file-streams:
  enabled: true
  s3:
    bucket: application-files
    region: eu-west-1
  limits:
    max-object-size: 512MB
    max-buffer-size: 1MB
    max-concurrent-transfers: 2
  http:
    enabled: true
```

AWS credentials use the normal SDK chain (environment, profile, workload role). No access keys belong in this configuration. Omitting `s3.endpoint` uses AWS; set it for compatible storage. HTTP exposure and the integration itself are disabled by default.

[Version 0.1.0: build, scenarios and examples](docs/0.1.0/en.md) · [All versions](docs/index.md) · [Complete reference](docs/0.1.0/reference-en.md)

Java 17+, Spring Boot 4.1.2, `file-streams` 0.1.0. Build the base library into its isolated local Maven repository first, then run `./gradlew build :example:installDist`; the [guide](docs/0.1.0/en.md#1-build-locally) has exact commands. Build artifacts locally; package registry publication is not yet available.

[MIT](LICENSE) · © 2026 Aleksandr Kashevarov.

</details>

<details name="readme-language" open>
<summary><picture><source media="(prefers-color-scheme: dark)" srcset="assets/lang-ru-dark.svg"><img src="assets/lang-ru-light.svg" width="42" height="26" alt="RU"></picture></summary>

## Потоковая передача файлов со Spring Boot

Зависимость и конфигурация подключают HTTP-загрузку и скачивание к существующему движку `file-streams`. Starter настраивает ограниченные S3-передачи, типизированные лимиты, стандартную цепочку credentials AWS и необязательный WebFlux endpoint. Аутентификацию, права на объекты, правила ключей и CORS задаёт приложение.

- **Настройка S3:** использует `S3Clients` и `S3ObjectStreams`; снаружи один поток, внутри ограниченный AWS multipart.
- **HTTP по явному включению:** PUT/GET `/objects/{encodedKey}` через готовый `ObjectStreamHandler`.
- **Ресурсы:** созданные клиент и credentials закрываются вместе с контекстом; пользовательские beans сохраняют объявленный жизненный цикл Spring.
- **Замена компонентов:** можно предоставить `ObjectStreams`, `StreamLimits`, `S3AsyncClient`, `AwsCredentialsProvider` или `ObjectStreamHandler`.
- **Примеры:** [Boot-приложение](example/src/main/java/example/FileStreamsApplication.java), [конфигурация](example/src/main/resources/application.yml), [скрипт запуска](scripts/run-example.sh). Комментарии идут EN, затем RU.

```yaml
file-streams:
  enabled: true
  s3:
    bucket: application-files
    region: eu-west-1
  limits:
    max-object-size: 512MB
    max-buffer-size: 1MB
    max-concurrent-transfers: 2
  http:
    enabled: true
```

AWS credentials берутся из стандартной цепочки SDK: окружение, профиль или роль среды исполнения. Ключи доступа не входят в эту конфигурацию. Без `s3.endpoint` используется AWS; для совместимого хранилища задайте endpoint. Интеграция и HTTP-маршруты по умолчанию выключены.

[Руководство 0.1.0: сборка, сценарии и примеры](docs/0.1.0/ru.md) · [Все версии](docs/index.md) · [Полный справочник](docs/0.1.0/reference-ru.md)

Java 17+, Spring Boot 4.1.2, `file-streams` 0.1.0. Сначала соберите базовую библиотеку в её отдельный Maven-каталог, затем выполните `./gradlew build :example:installDist`; точные команды приведены в [руководстве](docs/0.1.0/ru.md#1-локальная-сборка). Собирайте артефакты локально; пакеты пока не опубликованы в реестрах.

[MIT](LICENSE) · © 2026 Aleksandr Kashevarov.

</details>
