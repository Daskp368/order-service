# Этап 16. Docker и Docker Compose

## Результат этапа

Проект подготовлен к воспроизводимому контейнерному запуску. Одна команда должна:

1. собрать исполняемый Jar приложения;
2. создать Docker-образ Spring Boot приложения;
3. запустить PostgreSQL 15;
4. дождаться готовности PostgreSQL принимать подключения;
5. запустить приложение;
6. передать приложению настройки базы, JWT и первого администратора;
7. автоматически применить миграцию Flyway `V1__create_users_and_orders.sql`.

Команда запуска:

```bash
docker compose up --build
```

Контрольный запуск выполнен на Docker Desktop для Apple silicon. Compose собрал образ приложения, запустил
PostgreSQL 15 и Spring Boot, а приложение успешно подключилось к БД по адресу `postgres:5432`.

Фактически подтверждено:

- Docker Engine `29.8.0` и Docker Compose `5.5.1` доступны из Terminal;
- оба контейнера работают, а `postgres` имеет статус `healthy`;
- финальный контейнер приложения использует Eclipse Temurin Java `17.0.20`;
- Flyway увидел пустую схему и успешно применил миграцию `V1`;
- в базе созданы `flyway_schema_history`, `users` и `orders`;
- OpenAPI и Swagger UI отвечают с HTTP-статусом `200`;
- именованный volume `order-service_postgres_data` создан и подключён.
- после удаления и пересоздания контейнеров без флага `-v` Flyway сохранил версию `1` и сообщил, что
  схема актуальна; OpenAPI снова ответил `200`.

## Что добавлено и изменено

| Статус | Файл | Назначение |
|---|---|---|
| Новый | `Dockerfile` | Собирает Jar и создаёт образ приложения |
| Новый | `docker-compose.yml` | Описывает сервисы `app` и `postgres` |
| Новый | `.dockerignore` | Не передаёт Docker лишние файлы и секретный `.env` |
| Изменён | `pom.xml` | Задаёт стабильное имя `target/order-service.jar` |
| Изменён | `.env.example` | Добавляет внешний порт контейнерной PostgreSQL |
| Изменён | `README.md` | Добавляет команды запуска этапа 16 |
| Новый | `docs/stage-16-docker-compose.md` | Объясняет Docker и проверку результата |

Java-классы, API, бизнес-правила, миграции и схема базы на этом этапе не изменяются.

## Зачем нужен Docker

Без Docker приложение зависит от состояния конкретного компьютера. Нужно отдельно установить подходящую
Java, Maven и PostgreSQL, создать базу, пользователя, настроить порты и следить за версиями. На другом
компьютере набор программ или настроек может отличаться, поэтому работающий у разработчика проект иногда
не запускается у другого человека.

Docker упаковывает приложение вместе с его средой запуска в образ. Из одного образа можно многократно
создавать одинаковые контейнеры. В этом проекте Docker решает две отдельные задачи:

- создаёт предсказуемую среду Java 17 для Spring Boot;
- запускает заранее выбранную версию PostgreSQL 15 с нужной базой и постоянным хранилищем.

Docker не заменяет Java, Spring Boot или PostgreSQL. Он запускает их в изолированных контейнерах и связывает
контейнеры общей виртуальной сетью.

## Общая схема запуска

```text
macOS
│
└── Docker Desktop
    │
    └── Docker Compose project order-service
        │
        ├── container app
        │   ├── Java 17 JRE
        │   ├── app.jar
        │   └── порт контейнера 8080
        │
        ├── container postgres
        │   ├── PostgreSQL 15
        │   └── порт контейнера 5432
        │
        ├── сеть order-service_default
        │   └── app находит БД по имени postgres
        │
        └── volume order-service_postgres_data
            └── файлы базы сохраняются вне жизненного цикла контейнера
```

Браузер на macOS обращается к приложению через `http://localhost:8080`. Само приложение находится внутри
контейнера и подключается к базе по адресу `postgres:5432`.

## Основные понятия

### Docker Engine

Docker Engine выполняет настоящую работу: собирает образы, создаёт контейнеры, сети и volumes. Команда
`docker` является клиентом, который отправляет Engine команды.

На macOS контейнеры Linux нельзя запускать непосредственно как обычные процессы macOS. Docker Desktop
создаёт и обслуживает небольшую Linux-среду, внутри которой работает Docker Engine.

### Docker Desktop

Docker Desktop — приложение для macOS, которое устанавливает Docker Engine, команду `docker`, Docker
Compose и графический интерфейс. Окно Docker Desktop можно закрыть, но сам Docker продолжит работать,
пока приложение не завершено через пункт `Quit Docker Desktop`.

### Image

Image, или образ, — неизменяемый шаблон файловой системы и команды запуска. Наш образ приложения содержит
Java 17 JRE и `app.jar`. Образ PostgreSQL берётся из готового официального образа `postgres:15`.

Образ сам по себе ничего не выполняет. Это приблизительно как шаблон, из которого создаётся запущенный
экземпляр.

### Container

Container, или контейнер, — запущенный экземпляр образа. Один образ можно использовать для создания
нескольких контейнеров. Контейнер имеет собственные процессы, файловую систему, переменные окружения и
сетевые адреса, но использует ресурсы компьютера через Docker Engine.

Если контейнер удалить, изменения в его внутренней файловой системе обычно исчезнут. Поэтому данные базы
хранятся не внутри удаляемого слоя контейнера, а в отдельном volume.

### Layer

Каждая значимая инструкция Dockerfile создаёт слой образа. Docker умеет повторно использовать неизменившиеся
слои. Поэтому зависимости Maven загружаются после копирования `pom.xml`, а исходный код копируется позже.
Изменение Java-класса не должно заставлять Docker заново загружать все зависимости, если `pom.xml` не менялся.

### Port

Контейнер имеет собственные порты, которые по умолчанию недоступны через `localhost` компьютера. Запись
`8080:8080` означает:

```text
localhost:8080 на macOS  ->  port 8080 контейнера app
```

Для PostgreSQL используется:

```text
localhost:5433 на macOS  ->  port 5432 контейнера postgres
```

Внешний порт `5433` выбран, чтобы не конфликтовать с локальной PostgreSQL, которая может уже занимать
`5432`. Между контейнерами приложение всё равно использует внутренний порт `5432`.

### Volume

Volume — управляемое Docker постоянное хранилище. В проекте volume `postgres_data` подключается к каталогу
`/var/lib/postgresql/data` контейнера PostgreSQL.

Контейнер базы можно удалить и создать заново, а volume сохранит таблицы и записи. Команда
`docker compose down` не удаляет именованный volume. Команда `docker compose down -v` удаляет и контейнеры,
и volume, поэтому данные базы будут потеряны.

### Environment

Environment — переменные окружения контейнера. Они передают настройки без записи секретов в образ:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — подключение Spring Boot к PostgreSQL;
- `JWT_SECRET` — ключ подписи JWT;
- `ADMIN_USERNAME`, `ADMIN_PASSWORD` — создание первого администратора;
- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` — первоначальная настройка контейнера PostgreSQL.

`.env` находится только на компьютере разработчика и исключён из Git и Docker build context. Значения
переменных подставляет Docker Compose при чтении `docker-compose.yml`.

### Docker Compose

Docker Compose запускает несколько связанных сервисов по одному YAML-файлу. В проекте сервис `postgres`
отвечает за базу, а сервис `app` — за Spring Boot приложение. Compose также создаёт общую сеть и volume.

Без Compose пришлось бы отдельно создавать volume, сеть и два контейнера длинными командами `docker run`,
а затем вручную связывать их параметрами.

## Установка Docker Desktop на macOS

### 1. Узнать архитектуру Mac

В Terminal выполните:

```bash
uname -m
```

- `arm64` означает Apple silicon: M1, M2, M3, M4 или более новый процессор Apple;
- `x86_64` означает Mac с процессором Intel.

Это важно, потому что на странице загрузки есть отдельные установщики.

### 2. Скачать установщик

Откройте официальную страницу:

<https://docs.docker.com/desktop/setup/install/mac-install/>

Выберите установщик для Apple silicon или Intel в соответствии с результатом `uname -m`.

### 3. Установить приложение вручную

1. Откройте загруженный файл `Docker.dmg` двойным щелчком.
2. Перетащите значок Docker в папку `Applications`.
3. Откройте Finder.
4. Перейдите в `Applications`.
5. Дважды нажмите `Docker.app`.
6. Примите лицензионное соглашение Docker Desktop.
7. Для учебного компьютера выберите рекомендуемые настройки.
8. Если macOS запросит пароль пользователя, подтвердите установку системных компонентов.
9. Дождитесь сообщения в Docker Desktop, что Engine запущен.

Установка Docker Desktop и запуск контейнеров — разные действия. Установленное приложение после перезагрузки
Mac может быть не запущено, если автозапуск отключён.

## Как вручную запускать Docker

Перед работой с проектом Docker Desktop должен быть запущен.

### Через Finder

1. Откройте Finder.
2. Выберите `Applications`.
3. Найдите `Docker`.
4. Дважды нажмите `Docker.app`.
5. Дождитесь окончания запуска Engine. Команды проекта не следует выполнять во время состояния `Starting`.

### Через поиск macOS

1. Нажмите `Command + Space`.
2. Введите `Docker`.
3. Нажмите Enter.
4. Дождитесь готовности Docker Engine.

### Через Terminal

Это не основной ручной способ, но приложение можно открыть командой:

```bash
open -a Docker
```

После запуска проверьте Engine:

```bash
docker --version
docker compose version
docker info
```

Первые две команды проверяют наличие клиентов. `docker info` дополнительно связывается с Engine. Если клиент
установлен, но Docker Desktop ещё не запущен, `docker info` вернёт ошибку подключения к Docker daemon.

### Как завершить Docker Desktop

1. Откройте меню Docker Desktop в строке меню macOS.
2. Выберите `Quit Docker Desktop`.

Перед завершением желательно остановить проект командой `docker compose down`. Простое закрытие основного
окна Docker Desktop не обязательно завершает Engine.

## Подготовка `.env`

Compose автоматически читает `.env` из корня проекта. В проекте этот файл уже используется для локального
запуска Spring Boot.

Если `.env` уже существует, не копируйте шаблон поверх него: это может уничтожить локальные настройки.
Откройте файл и убедитесь, что в нём заданы:

```properties
DB_URL=jdbc:postgresql://localhost:5432/orders_db
DB_USERNAME=orders_app
DB_PASSWORD=<надёжный пароль базы>
POSTGRES_HOST_PORT=5433
JWT_SECRET=<Base64-ключ длиной не менее 32 байт после декодирования>
ADMIN_USERNAME=local_admin
ADMIN_PASSWORD=<надёжный пароль администратора>
```

Если `.env` ещё не существует, создайте его из шаблона:

```bash
cp .env.example .env
```

Затем обязательно замените значения-заглушки. JWT-секрет можно создать так:

```bash
openssl rand -base64 32
```

`DB_URL` в `.env` остаётся адресом для обычного локального запуска через Maven. В контейнере Compose явно
переопределяет его значением `jdbc:postgresql://postgres:5432/orders_db`.

## Первый ручной запуск проекта

### 1. Запустить Docker Desktop

Запустите Docker через `Applications` или Spotlight и дождитесь готовности Engine.

### 2. Открыть Terminal и перейти в проект

```bash
cd '/Users/pavelshadrin/Desktop/Обучение/Java/Модуль 6 Spring/order-service'
```

Все следующие команды нужно выполнять из каталога, где находится `docker-compose.yml`.

### 3. Проверить итоговую конфигурацию

```bash
docker compose config
```

Команда читает `docker-compose.yml`, подставляет значения из `.env` и проверяет структуру конфигурации.
Её вывод может содержать подставленные секреты, поэтому не копируйте полный вывод в публичные сообщения.

### 4. Собрать и запустить проект

```bash
docker compose up --build
```

- `docker compose` запускает Compose;
- `up` создаёт и запускает необходимые ресурсы;
- `--build` пересобирает образ приложения перед запуском.

При первом запуске Docker загрузит базовые образы Java и PostgreSQL, а Maven внутри build-контейнера загрузит
зависимости. Поэтому первый запуск обычно дольше следующих и требует доступа в интернет.

Команда работает в foreground-режиме и показывает общие логи двух контейнеров. Для остановки нажмите
`Ctrl+C`.

### 5. Фоновый запуск

Если терминал должен остаться свободным:

```bash
docker compose up --build -d
```

Флаг `-d` означает detached mode. Контейнеры продолжают работать в фоне.

Проверить состояние:

```bash
docker compose ps
```

Посмотреть логи приложения:

```bash
docker compose logs -f app
```

Посмотреть логи PostgreSQL:

```bash
docker compose logs -f postgres
```

Выйти из просмотра логов можно через `Ctrl+C`; контейнеры при этом продолжат работать.

### 6. Открыть приложение

После успешного запуска доступны:

```text
Swagger UI:   http://localhost:8080/swagger-ui.html
OpenAPI JSON: http://localhost:8080/v3/api-docs
OpenAPI YAML: http://localhost:8080/v3/api-docs.yaml
```

PostgreSQL контейнера доступна с macOS по `localhost:5433`. Для работы самого приложения этот внешний порт
не используется.

## Команды управления

### Показать работающие сервисы

```bash
docker compose ps
```

### Остановить контейнеры без удаления

```bash
docker compose stop
```

Повторно запустить уже созданные контейнеры:

```bash
docker compose start
```

### Перезапустить только приложение

```bash
docker compose restart app
```

### Остановить и удалить контейнеры и сеть

```bash
docker compose down
```

Именованный volume PostgreSQL сохраняется. При следующем `up` приложение увидит прежние данные.

### Полностью удалить контейнерную базу

```bash
docker compose down -v
```

Флаг `-v` удаляет volume с таблицами и записями. Эта операция необратима для данных внутри volume. Она нужна
только для намеренного чистого прогона Flyway или полного сброса учебной базы.

### Пересобрать образ после изменения Java-кода

```bash
docker compose up --build -d
```

### Принудительно пересобрать все слои

```bash
docker compose build --no-cache app
docker compose up -d
```

Обычно `--no-cache` не нужен: кэш Docker ускоряет сборку.

## Как работает Dockerfile

Dockerfile состоит из двух этапов.

### Этап builder

```dockerfile
FROM eclipse-temurin:17-jdk-noble AS builder
```

Создаётся временный этап с полным JDK. JDK нужен Maven для компиляции Java-кода.

```dockerfile
WORKDIR /workspace
```

Следующие относительные пути и команды выполняются в `/workspace`.

```dockerfile
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
```

Сначала копируются Maven Wrapper и `pom.xml`, затем загружаются зависимости. Этот слой переиспользуется,
пока `pom.xml` не изменился.

```dockerfile
COPY src src
RUN ./mvnw -B -ntp -DskipTests package
```

Исходный код копируется после зависимостей. Maven компилирует приложение и создаёт
`target/order-service.jar`. Тесты в image build не запускаются, потому что полный интеграционный набор требует
отдельной PostgreSQL. Они выполняются самостоятельной проверкой до контейнерной сборки.

### Этап запуска

```dockerfile
FROM eclipse-temurin:17-jre-noble
```

Финальный образ использует JRE. Компилятор и Maven запущенному приложению не нужны, поэтому они не попадают
в runtime-образ.

```dockerfile
WORKDIR /app
COPY --from=builder /workspace/target/order-service.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Из builder копируется только готовый Jar. `EXPOSE` документирует порт приложения, а `ENTRYPOINT` задаёт
процесс контейнера.

## Как работает Docker Compose

### Сервис PostgreSQL

```yaml
postgres:
  image: postgres:15
```

Если образ отсутствует локально, Docker загружает его. Тег `15` фиксирует требуемую ТЗ основную версию.

Переменные `POSTGRES_DB`, `POSTGRES_USER` и `POSTGRES_PASSWORD` обрабатывает стартовый скрипт официального
образа PostgreSQL при первом создании пустого volume.

Если volume уже содержит базу, изменение этих переменных не переименует существующую базу и пользователя.
Они используются для первоначальной инициализации, а не как миграция существующих данных.

### Healthcheck

```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
```

Запущенный процесс PostgreSQL не всегда уже готов принимать соединения. `pg_isready` проверяет именно
готовность сервера. Двойной знак `$` нужен, чтобы Compose не подставлял значение заранее, а передал переменную
для раскрытия внутри контейнера PostgreSQL.

### Сервис приложения

```yaml
app:
  build:
    context: .
    dockerfile: Dockerfile
```

`context: .` передаёт Docker файлы из корня проекта. `.dockerignore` исключает ненужные элементы.

```yaml
depends_on:
  postgres:
    condition: service_healthy
```

Короткая форма `depends_on: - postgres` гарантировала бы только порядок запуска контейнеров. Условие
`service_healthy` заставляет Compose дождаться успешного healthcheck базы и лишь затем создавать `app`.

## Почему используется `postgres`, а не `localhost`

`localhost` всегда означает текущую сетевую среду процесса.

```text
На macOS:
localhost -> компьютер Mac

Внутри container app:
localhost -> container app
```

PostgreSQL работает в другом контейнере, поэтому `jdbc:postgresql://localhost:5432/orders_db` внутри `app`
искал бы базу в самом контейнере приложения и завершился ошибкой соединения.

Compose создаёт сеть по умолчанию и внутреннюю DNS-запись с именем каждого сервиса:

```text
service app -> DNS name postgres -> container postgres:5432
```

Поэтому контейнерное значение выглядит так:

```text
jdbc:postgresql://postgres:5432/orders_db
```

IP-адрес контейнера указывать нельзя: он может измениться при пересоздании. Имя сервиса остаётся стабильным.

## Что происходит при `docker compose up --build`

1. Compose читает `docker-compose.yml` и `.env`.
2. Проверяет обязательные переменные.
3. Docker собирает builder-этап приложения.
4. Maven Wrapper загружает Maven и зависимости, затем создаёт Jar.
5. Docker создаёт финальный runtime-образ приложения.
6. Docker получает образ `postgres:15`, если его ещё нет локально.
7. Compose создаёт сеть `order-service_default`.
8. Compose создаёт или подключает volume `order-service_postgres_data`.
9. Запускается контейнер `postgres`.
10. Healthcheck ожидает готовности PostgreSQL.
11. После статуса `healthy` запускается контейнер `app`.
12. Spring Boot создаёт DataSource по переданным переменным.
13. Flyway проверяет `flyway_schema_history` и применяет отсутствующие миграции.
14. Hibernate с `ddl-auto=validate` проверяет соответствие Entity готовой схеме.
15. Включается HTTP-сервер на порту контейнера `8080`.
16. Проброс порта делает приложение доступным на `localhost:8080` компьютера.

## Проверка Flyway и сохранения данных

В логах приложения найдите сообщения Flyway о подключении к PostgreSQL, проверке миграций и переходе схемы
на версию `1`. При повторном запуске Flyway не создаёт таблицы заново, а подтверждает актуальность схемы.

Проверить наличие таблиц можно через `psql` внутри контейнера:

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\\dt"'
```

Одинарные кавычки не позволяют Terminal раскрыть переменные заранее. Их значения берутся из окружения
контейнера PostgreSQL.

Для проверки volume:

1. зарегистрируйте тестового пользователя через Swagger UI;
2. выполните `docker compose down`;
3. снова выполните `docker compose up -d`;
4. войдите теми же учётными данными.

Пользователь должен сохраниться, потому что данные находятся в volume.

## Возможные ошибки

### `docker: command not found`

Docker Desktop не установлен либо каталог Docker CLI отсутствует в `PATH`. Установите Docker Desktop,
перезапустите Terminal и повторите `docker --version`.

### `Cannot connect to the Docker daemon`

Команда `docker` установлена, но Docker Engine не запущен. Откройте Docker Desktop и дождитесь завершения
состояния `Starting`.

### `port is already allocated` для 8080

Порт приложения занят другим процессом. Остановите прежний экземпляр Spring Boot или контейнер, который
использует порт 8080.

Проверить контейнеры:

```bash
docker ps
```

### `port is already allocated` для 5433

Измените в `.env` значение `POSTGRES_HOST_PORT`, например:

```properties
POSTGRES_HOST_PORT=5434
```

Внутренний JDBC URL менять не нужно: контейнеры продолжат общаться через `postgres:5432`.

### Compose сообщает об отсутствующей переменной

Проверьте `.env`. `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_USERNAME` и `ADMIN_PASSWORD` обязательны
для контейнерного запуска.

### Ошибка `JWT_SECRET`

Секрет должен быть Base64-строкой, которая после декодирования содержит не менее 32 байт. Создайте новый:

```bash
openssl rand -base64 32
```

### Приложение завершилось после запуска

Сначала посмотрите его логи:

```bash
docker compose logs app
```

Затем проверьте состояние базы:

```bash
docker compose ps
docker compose logs postgres
```

Не удаляйте volume до чтения логов: удаление уничтожит состояние, которое помогает найти причину.

## Контрольная точка этапа

Этап считается завершённым, когда подтверждены все пункты:

- Docker Desktop установлен и Docker Engine запущен;
- `docker compose config` завершается без ошибки;
- `docker compose up --build` собирает образ и запускает оба сервиса;
- сервис `postgres` получает статус `healthy`;
- приложение подключается к БД по имени `postgres`;
- Flyway применяет `V1` на чистом volume;
- Swagger UI открывается на `http://localhost:8080/swagger-ui.html`;
- после `docker compose down` и нового запуска данные остаются в volume;
- полный набор тестов Maven по-прежнему проходит.

Все пункты контрольной точки подтверждены фактическим запуском. Дополнительно полный набор Maven содержит
`103 tests`: `0 failures`, `0 errors`, `0 skipped`.

## Практическое задание

После первого успешного запуска измените только `POSTGRES_HOST_PORT` с `5433` на `5434`, пересоздайте
контейнеры и объясните:

1. почему подключение с macOS изменилось на `localhost:5434`;
2. почему JDBC URL приложения остался `postgres:5432`;
3. почему данные в базе не исчезли.

## Контрольные вопросы

1. Чем Docker image отличается от запущенного container?
2. Зачем Dockerfile использует отдельные этапы `builder` и runtime?
3. Почему `depends_on` с условием `service_healthy` надёжнее простого порядка запуска?
4. Почему приложение внутри контейнера подключается к `postgres`, а не к `localhost`?
5. Чем `docker compose down` отличается от `docker compose down -v`?

## Официальная документация

- [Установка Docker Desktop на macOS](https://docs.docker.com/desktop/setup/install/mac-install/)
- [Первое знакомство с Docker Compose](https://docs.docker.com/compose/gettingstarted/)
- [Сеть Docker Compose](https://docs.docker.com/compose/how-tos/networking/)
- [Порядок запуска и готовность сервисов](https://docs.docker.com/compose/how-tos/startup-order/)
