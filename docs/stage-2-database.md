# Этап 2. Подключение PostgreSQL

## Результат

Приложение подключается к пустой базе `orders_db` на локальном PostgreSQL 17 от имени отдельной роли `orders_app`.

## Компоненты подключения

```text
Spring Boot
    -> DataSource
    -> HikariCP
    -> PostgreSQL JDBC Driver
    -> PostgreSQL
    -> orders_db
```

- PostgreSQL — отдельный сервер базы данных.
- JDBC — стандарт Java для общения с реляционными базами.
- PostgreSQL Driver — реализация JDBC для протокола PostgreSQL.
- DataSource — объект, через который приложение запрашивает соединения.
- HikariCP — пул, который переиспользует открытые соединения.
- JPA — стандарт преобразования Java-объектов в записи таблиц.
- Hibernate — используемая Spring Boot реализация JPA.
- Spring Data JPA — надстройка для создания репозиториев.

## Зависимости

`spring-boot-starter-data-jpa` подключает JPA, Hibernate, Spring Data JPA, JDBC и HikariCP.

Драйвер PostgreSQL имеет область `runtime`: он необходим запущенному приложению, но наш исходный код не обращается к его классам напрямую.

## Настройки

Файл `application.properties` содержит только ссылки на внешние значения:

```properties
spring.config.import=optional:file:.env[.properties]
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

`.env` используется только локально и исключён из Git. `.env.example` показывает требуемые имена переменных без настоящего пароля.

`spring.jpa.hibernate.ddl-auto=none` запрещает Hibernate создавать или изменять таблицы. Схемой базы будет управлять Flyway на этапе 3.

`spring.jpa.open-in-view=false` ограничивает работу JPA слоем бизнес-логики и не оставляет EntityManager открытым на всё время обработки HTTP-запроса.

## Как прочитать JDBC URL

```text
jdbc:postgresql://localhost:5432/orders_db
│       │             │       │
│       │             │       └── база данных
│       │             └────────── порт
│       └──────────────────────── сервер
└──────────────────────────────── JDBC-протокол
```

`localhost` означает этот компьютер. Когда приложение позже окажется в Docker, `localhost` будет означать уже контейнер приложения, поэтому адрес изменится.

## Признаки успешного соединения

В логах теста появились строки:

```text
HikariPool-1 - Added connection
HikariPool-1 - Start completed
Database version: 17.2
Initialized JPA EntityManagerFactory
```

Это доказывает, что драйвер загрузился, PostgreSQL принял учётные данные, HikariCP создал соединение и Hibernate инициализировался.

## Команды

Проверка доступности сервера:

```bash
/Library/PostgreSQL/17/bin/pg_isready -h localhost -p 5432
```

Проверка Spring Context и соединения:

```bash
./mvnw test
```

Запуск приложения:

```bash
./mvnw spring-boot:run
```

## Контрольные вопросы

1. Чем сервер PostgreSQL отличается от базы `orders_db`?
2. Зачем приложению JDBC-драйвер?
3. Почему HikariCP хранит несколько соединений вместо открытия нового для каждого запроса?
4. Чем JPA отличается от Hibernate?
5. Почему пароль находится в `.env`, а не в `application.properties`?
6. Почему Hibernate запрещено самостоятельно создавать таблицы?
