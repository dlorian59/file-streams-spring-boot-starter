# Verification / Проверки — 2026-11-04

[← Versions / Версии](index.md) · [README](../README.md)

| Check / Проверка | Result / Результат |
| --- | --- |
| Spring Boot 4.1.2, Java 17 / 21 / 25 | Six auto-configuration tests pass on each runtime: imports, defaults, typed bounds, invalid settings, backoff, resource lifecycle, HTTP opt-in. / По шесть тестов на каждой JDK. |
| Java 27 | Not run: the installed toolchain symlink points to a missing directory. CI configuration includes 27, but CI has not run. / Не выполнено: локальная ссылка JDK указывает на отсутствующий каталог. |
| Bytecode | Main library and example classes target Java 17 (major 61). / Библиотека и пример собираются для Java 17. |
| Build | JAR, sources, Javadoc, configuration metadata, auto-configuration imports, MIT and runnable example distribution built. Published only to an isolated local Maven directory. / Артефакты собраны и помещены только в локальный Maven-каталог. |
| HTTP routing | Real Boot server: independent `/example/status` RouterFunction returns 200; starter `/objects/missing` returns structured 404. / Обычный маршрут и endpoint starter работают одновременно. |
| Browser → Spring | Actual headless Chromium HTTPS/HTTP2 upload of 45 bytes, confirmed commit and streamed download with byte equality; zero page errors. Destination is a test writable sink. / Реальная браузерная загрузка, commit и совпадающее скачивание. |
| Known-length cancellation | Declared 1 MiB, cancelled before EOF: client `ABORTED` / unknown; subsequent GET 404. / Отмена до EOF при объявленной длине, последующий GET 404. |
| Unknown-length cancellation | Client `ABORTED` / unknown; fixture retained an 8,192-byte prefix. Exact HTTP termination cause is not established. / Отмена с неизвестной длиной оставила префикс 8 192 байта; причина на уровне транспорта не установлена. |
| S3 backend | Blocked: Docker could not start Garage because an existing parent snapshot was missing. No global Docker repair was attempted. / Новый S3 E2E заблокирован ошибкой Docker snapshot. |

[Recorded browser/server observations / Запись наблюдений](evidence/browser-spring.json).

The browser tests use the real starter, server, HTTP handler and browser package with deliberately small in-memory test storage. They do not establish S3 cleanup, throughput, memory bounds for arbitrary deployments or reverse-proxy support. Cancellation is not rollback; when the final size is known, send `knownLength` / `X-Upload-Length` so a short body can be rejected. The fixture has per-file and retained-storage limits and at most two active uploads; it is compiled in a separate `transportFixture` source set and excluded from production distributions.

Браузерные проверки используют настоящий starter, сервер, HTTP-адаптер и браузерный пакет с небольшим тестовым хранилищем в памяти. Они не подтверждают очистку S3, производительность, память произвольного окружения или поддержку reverse proxy. Отмена не является откатом. Когда размер известен, передавайте `knownLength` / `X-Upload-Length`: это позволяет отклонить неполное тело. Fixture ограничивает размер файла, сохранённые данные и две активные загрузки; он вынесен в отдельный source set и не входит в production distribution.

GitHub account access is unavailable in this session; remote repository creation, CI execution and Maven/npm registry publication have not been performed. / Доступ к GitHub недоступен; удалённый репозиторий, CI и публикация в реестры не выполнены.

[← Versions / Версии](index.md) · [README](../README.md)
