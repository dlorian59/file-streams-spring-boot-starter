# Consumer development / Проверка consumer

[← Consumer](README.md) · [Library / Библиотека](../../README.md)

Development commands / Команды разработки (from starter root / из корня starter):

```bash
(cd ../file-streams && ./gradlew publishToMavenLocal -Dmaven.repo.local="$PWD/build/local-repository")
./gradlew publishToMavenLocal -Dmaven.repo.local="$PWD/build/local-repository"
./gradlew -p examples/consumer test
mvn -f examples/consumer/pom.xml test
```

The primary root README contains dependency/configuration integration only. These commands build artifacts while registry publication is pending. Replace test credentials with the existing application's security setup; never copy the test's public password or CSRF setting to production.

Корневой README содержит только подключение dependency/configuration. Команды здесь собирают артефакты до публикации в реестры. В приложении сохраните свою безопасность; не переносите публичный пароль и настройку CSRF из теста в production.
