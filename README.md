![Логотип UI for Apache Kafka](documentation/images/kafka-ui-logo.png) UI for Apache Kafka
------------------
#### Универсальный, быстрый и лёгкий веб-интерфейс для управления кластерами Apache Kafka®.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/provectus/kafka-ui/blob/master/LICENSE)
![UI for Apache Kafka Price Free](documentation/images/free-open-source.svg)
[![Release version](https://img.shields.io/github/v/release/provectus/kafka-ui)](https://github.com/provectus/kafka-ui/releases)
[![Chat with us](https://img.shields.io/discord/897805035122077716)](https://discord.gg/4DWzD7pGE5)

### Отказ от ответственности
*UI for Apache Kafka — бесплатный инструмент, который развивается и поддерживается open-source сообществом. Проект курирует Provectus: он останется бесплатным и открытым, без платных функций и подписок. Нужна помощь экспертов по Kafka? Provectus помогает проектировать, разворачивать и сопровождать кластеры и streaming-приложения. Подробнее: [Professional Services for Apache Kafka](https://provectus.com/professional-services-apache-kafka/).*

UI for Apache Kafka — бесплатный open-source веб-UI для мониторинга и управления кластерами Apache Kafka. Инструмент помогает наблюдать потоки данных, быстрее находить проблемы и следить за производительностью. Лёгкий дашборд показывает ключевые метрики: брокеры, топики, партиции, production и consumption.

![Interface](documentation/images/Interface.gif)

## Содержание

1. [Возможности](#возможности)
2. [Интерфейс](#интерфейс)
3. [Что есть в этом форке](#что-есть-в-этом-форке)
4. [Роли и API пользователей](#роли-и-api-пользователей)
5. [Переменные окружения](#переменные-окружения)
6. [Сборка образа](#сборка-образа)
7. [Развёртывание в Docker Compose](#развёртывание-в-docker-compose)
8. [Развёртывание в Kubernetes](#развёртывание-в-kubernetes)
9. [Helm](#helm)
10. [Миграция со схемы admin + readonly](#миграция-со-схемы-admin--readonly)
11. [Сборка и запуск из исходников](#сборка-и-запуск-из-исходников)
12. [Health-пробы](#health-пробы)
13. [Дополнительные гайды](#дополнительные-гайды)

---

## Возможности

* **Управление несколькими кластерами** — все кластеры в одном месте
* **Мониторинг производительности** — ключевые метрики Kafka на лёгком дашборде
* **Просмотр брокеров** — назначения топиков/партиций, статус контроллера
* **Просмотр топиков** — число партиций, репликация, кастомная конфигурация
* **Просмотр consumer groups** — offsets и lag по партициям
* **Просмотр сообщений** — JSON, plain text, Avro
* **Динамическая конфигурация топиков** — создание и настройка топиков
* **Аутентификация** — OAuth 2.0 (GitHub/GitLab/Google и др.), LDAP, форма входа
* **Плагины сериализации** — готовые serde или собственные

В этом форке дополнительно:

* несколько локальных учётных записей с ролями `READ` / `READ_WRITE`;
* постоянное хранение пользователей в Docker volume / Kubernetes PVC;
* постоянное хранение списка Kafka-серверов через env / ConfigMap.

---

## Интерфейс

UI for Apache Kafka закрывает основные операции Apache Kafka удобным интерфейсом.

![Interface](documentation/images/Interface.gif)

### Топики

Создание топиков в браузере и просмотр списка:

![Create Topic](documentation/images/Create_topic_kafka-ui.gif)

Навигация между connectors, topics и consumers:

![Connector_Topic_Consumer](documentation/images/Connector_Topic_Consumer.gif)

### Сообщения

Отправка сообщений в топик и просмотр списка:

![Produce Message](documentation/images/Create_message_kafka-ui.gif)

### Schema Registry

Поддерживаются Avro®, JSON Schema и Protobuf:

![Create Schema Registry](documentation/images/Create_schema.gif)

![Avro Schema Topic](documentation/images/Schema_Topic.gif)

---

## Что есть в этом форке

### Учётные записи

В upstream `LOGIN_FORM` поддерживает **одного** пользователя на инстанс
(`SPRING_SECURITY_USER_NAME` / `SPRING_SECURITY_USER_PASSWORD`). Поэтому раньше
для readonly поднимали второй Deployment.

В этой сборке при `AUTH_TYPE=LOGIN_FORM`:

- пользователи хранятся в файле `users.json` (по умолчанию `/etc/kafkaui/users.json`);
- пароли пишутся только как **BCrypt-хеши**;
- роли:
  - `READ` — просмотр Kafka-данных (GET/HEAD/OPTIONS);
  - `READ_WRITE` — изменение Kafka-данных и управление учётными записями;
- первый пользователь создаётся при старте из bootstrap-переменных, если файла ещё нет;
- дальнейшее управление — через UI **User accounts** (`/ui/users`) или API `/api/auth/*`.

При `AUTH_TYPE=LDAP` файл `users.json` **не используется**. Роли `READ` /
`READ_WRITE` берутся из LDAP-групп (см. раздел LDAP ниже); управление
локальными пользователями (`/api/auth/users`) недоступно. Endpoint
`GET /api/auth/me` работает и для LDAP (нужен для кнопки Configure и навигации).

### Постоянные данные

| Данные | Где хранятся | Переживают redeploy? |
|--------|--------------|----------------------|
| Учётные записи | volume / PVC → `/etc/kafkaui/users.json` | Да, пока том не удалён |
| Список Kafka-кластеров (ConfigMap / env) | ConfigMap (K8s) или env (Docker) | Да |
| Список Kafka-кластеров (Configuration Wizard) | volume / PVC → `/etc/kafkaui/dynamic_config.yaml` | Да, пока том не удалён |
| Bootstrap-пароль | Secret (K8s) / env (Docker) | Только при первом создании `users.json` |

> Configuration Wizard из upstream `v0.7.2` **восстановлен** в этой ветке.
> Он включается переменной `DYNAMIC_CONFIG_ENABLED=true` (уже задана в
> `deploy/docker/docker-compose.yaml` и `deploy/k8s/manifests/kafka-ui/01-configmap.yaml`).
>
> - Кнопка **Configure** (рядом с кластером) и **Configure new cluster** на дашборде,
>   а также страницы мастера видны только пользователям с ролью `READ_WRITE`;
>   пользователи с ролью `READ` их не видят.
> - Изменения конфигурации через мастер сохраняются в
>   `/etc/kafkaui/dynamic_config.yaml` на том же томе / PVC, что и `users.json`,
>   поэтому переживают redeploy пода и теряются только при удалении тома.
> - Кластеры, заданные через ConfigMap / env (`KAFKA_CLUSTERS_0_*`), продолжают
>   работать независимо от `DYNAMIC_CONFIG_ENABLED`.
> - На backend доступ к `/api/config/**` требует роли `READ_WRITE`, а `GET /api/info`
>   разрешён обеим ролям.

### Образ

Upstream-образ `provectuslabs/kafka-ui` **не содержит** локального multi-user
хранилища. Нужен образ, собранный из этой ветки (`deploy/docker/Dockerfile`).

---

## Роли и API пользователей

| HTTP-метод | `READ` | `READ_WRITE` |
|------------|--------|--------------|
| GET / HEAD / OPTIONS | разрешено | разрешено |
| POST / PUT / PATCH / DELETE к Kafka API | запрещено | разрешено |
| `/api/auth/me` | разрешено | разрешено |
| `/api/auth/users` | запрещено | разрешено |
| `GET /api/info` | разрешено | разрешено |
| `/api/config`, `/api/config/**` (Configuration Wizard) | запрещено | разрешено |

Страница **User accounts** видна только пользователям с ролью `READ_WRITE`.

Ограничения:

- нельзя удалить собственную учётную запись;
- нельзя удалить или понизить последнего пользователя с `READ_WRITE`;
- имя пользователя: 3–64 символа (`A-Za-z0-9._@-`);
- пароль: минимум 8 символов.

Базовый путь API: `/api/auth` (нужна сессия после `/auth`).

| Метод | Путь | Кто | Описание |
|-------|------|-----|----------|
| `GET` | `/api/auth/me` | любой авторизованный | текущий пользователь и роль |
| `GET` | `/api/auth/users` | `READ_WRITE` | список пользователей |
| `POST` | `/api/auth/users` | `READ_WRITE` | создать пользователя |
| `PUT` | `/api/auth/users/{username}` | `READ_WRITE` | изменить роль / пароль |
| `DELETE` | `/api/auth/users/{username}` | `READ_WRITE` | удалить пользователя |

Пример:

```bash
curl -X POST 'https://kafka-ui.example/api/auth/users' \
  -H 'Content-Type: application/json' \
  -b cookies.txt \
  -d '{"username":"viewer","password":"viewer-password","role":"READ"}'
```

Ответ (без пароля):

```json
{"username":"viewer","role":"READ"}
```

---

## Переменные окружения

### Аутентификация и пользователи (этот форк)

| Переменная | Описание | По умолчанию |
|------------|----------|--------------|
| `AUTH_TYPE` | Тип аутентификации: `LOGIN_FORM`, `LDAP`, `OAUTH2`, `DISABLED` | `DISABLED` |
| `AUTH_LOCAL_USERS_FILE` | Путь к файлу пользователей (**только** `LOGIN_FORM`) | `/etc/kafkaui/users.json` |
| `AUTH_LOCAL_BOOTSTRAP_USERNAME` | Имя первого администратора (**только** `LOGIN_FORM`) | `admin` |
| `AUTH_LOCAL_BOOTSTRAP_PASSWORD` | Пароль первого администратора (**только** `LOGIN_FORM`) | обязателен при первом старте |

### LDAP (`AUTH_TYPE=LDAP`)

HTTP Basic + роль из LDAP-групп. Пример compose: `documentation/compose/auth-ldap.yaml`.
Пример Kubernetes ConfigMap/Secret: `documentation/k8s/auth-ldap-example.yaml`.

Подключение к каталогу — через `spring.ldap.*`:

| Переменная | Описание |
|------------|----------|
| `SPRING_LDAP_URLS` | URL LDAP / AD (`ldap://…`) |
| `SPRING_LDAP_BASE` | Базовый DN каталога; DN pattern и search base тогда задаются относительно него |
| `SPRING_LDAP_DN_PATTERN` | DN pattern пользователя, например `uid={0},ou=people,dc=example,dc=com` |
| `SPRING_LDAP_USERFILTER_SEARCHBASE` / `SPRING_LDAP_USERFILTER_SEARCHFILTER` | Альтернатива DN pattern (нужен bind) |
| `SPRING_LDAP_ADMINUSER` | Bind DN (ConfigMap допустим) |
| `SPRING_LDAP_ADMINPASSWORD` | Bind password — **только Secret**, не ConfigMap |

Роли приложения:

| Переменная | Property | Описание | По умолчанию |
|------------|----------|----------|--------------|
| `AUTH_LDAP_READ_GROUP` | `auth.ldap.read-group` | LDAP-группа → `READ` | — |
| `AUTH_LDAP_READ_WRITE_GROUP` | `auth.ldap.read-write-group` | LDAP-группа → `READ_WRITE` | — |
| `AUTH_LDAP_DEFAULT_ROLE` | `auth.ldap.default-role` | Роль, если группа не совпала: `READ`, `READ_WRITE` или `NONE` | `READ` |
| `AUTH_LDAP_GROUP_SEARCH_BASE` | `auth.ldap.group-search-base` | База поиска групп (non-AD). Пусто = без поиска групп | — |
| `AUTH_LDAP_GROUP_SEARCH_FILTER` | `auth.ldap.group-search-filter` | Фильтр; `{0}` = DN пользователя | `(member={0})` |
| `AUTH_LDAP_GROUP_ROLE_ATTRIBUTE` | `auth.ldap.group-role-attribute` | Атрибут имени группы | `cn` |
| `AUTH_LDAP_ACTIVE_DIRECTORY_ENABLED` | `auth.ldap.active-directory.enabled` | Режим Active Directory | `false` |
| `AUTH_LDAP_ACTIVE_DIRECTORY_DOMAIN` | `auth.ldap.active-directory.domain` | AD domain | — |

Сравнение имён групп — case-insensitive; допускаются формы `ROLE_…` и DN `CN=…`.
При AD группы берутся из ответа `ActiveDirectoryLdapAuthenticationProvider`
(отдельный group search не нужен). Legacy: `OAUTH2_LDAP_ACTIVEDIRECTORY` /
`oauth2.ldap.activeDirectory.domain` (в т.ч. старый ключ с кириллической «с»).

`GET /api/auth/me` → `{"username":"…","role":"READ"|"READ_WRITE"}`.
`/api/auth/users/**` для LDAP запрещён.

### Кластеры Kafka

Каждый параметр из YAML можно задать env-переменной. Например, `name` → `KAFKA_CLUSTERS_0_NAME`.

| Переменная | Описание |
|------------|----------|
| `SERVER_SERVLET_CONTEXT_PATH` | Базовый URI (basePath) |
| `LOGGING_LEVEL_ROOT` | Уровень логов (trace, debug, info, warn, error). По умолчанию: info |
| `LOGGING_LEVEL_COM_PROVECTUS` | Уровень логов приложения. По умолчанию: debug |
| `SERVER_PORT` | Порт. По умолчанию: `8080` |
| `KAFKA_ADMIN-CLIENT-TIMEOUT` | Таймаут Kafka API в мс. По умолчанию: `30000` |
| `KAFKA_CLUSTERS_0_NAME` | Имя кластера |
| `KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS` | Bootstrap-серверы |
| `KAFKA_CLUSTERS_0_KSQLDBSERVER` | Адрес KSQL DB |
| `KAFKA_CLUSTERS_0_KSQLDBSERVERAUTH_USERNAME` | Basic auth username для KSQL DB |
| `KAFKA_CLUSTERS_0_KSQLDBSERVERAUTH_PASSWORD` | Basic auth password для KSQL DB |
| `KAFKA_CLUSTERS_0_KSQLDBSERVERSSL_KEYSTORELOCATION` | JKS keystore для KSQL DB |
| `KAFKA_CLUSTERS_0_KSQLDBSERVERSSL_KEYSTOREPASSWORD` | Пароль keystore KSQL DB |
| `KAFKA_CLUSTERS_0_KSQLDBSERVERSSL_TRUSTSTORELOCATION` | JKS truststore для KSQL DB |
| `KAFKA_CLUSTERS_0_KSQLDBSERVERSSL_TRUSTSTOREPASSWORD` | Пароль truststore KSQL DB |
| `KAFKA_CLUSTERS_0_PROPERTIES_SECURITY_PROTOCOL` | Протокол к брокерам (`SSL` или не задавать для plaintext) |
| `KAFKA_CLUSTERS_0_SCHEMAREGISTRY` | Адрес Schema Registry |
| `KAFKA_CLUSTERS_0_SCHEMAREGISTRYAUTH_USERNAME` | Basic auth username Schema Registry |
| `KAFKA_CLUSTERS_0_SCHEMAREGISTRYAUTH_PASSWORD` | Basic auth password Schema Registry |
| `KAFKA_CLUSTERS_0_SCHEMAREGISTRYSSL_KEYSTORELOCATION` | JKS keystore Schema Registry |
| `KAFKA_CLUSTERS_0_SCHEMAREGISTRYSSL_KEYSTOREPASSWORD` | Пароль keystore Schema Registry |
| `KAFKA_CLUSTERS_0_SCHEMAREGISTRYSSL_TRUSTSTORELOCATION` | JKS truststore Schema Registry |
| `KAFKA_CLUSTERS_0_SCHEMAREGISTRYSSL_TRUSTSTOREPASSWORD` | Пароль truststore Schema Registry |
| `KAFKA_CLUSTERS_0_SCHEMANAMETEMPLATE` | Шаблон ключей в Schema Registry |
| `KAFKA_CLUSTERS_0_METRICS_PORT` | Порт метрик брокера |
| `KAFKA_CLUSTERS_0_METRICS_TYPE` | Тип метрик: `JMX` (по умолчанию) или `PROMETHEUS` |
| `KAFKA_CLUSTERS_0_READONLY` | Read-only режим **всего кластера** (независимо от роли пользователя). По умолчанию: false |
| `KAFKA_CLUSTERS_0_DISABLELOGDIRSCOLLECTION` | Отключить сбор информации о сегментах. Для Confluent Cloud: true |
| `KAFKA_CLUSTERS_0_KAFKACONNECT_0_NAME` | Имя Kafka Connect |
| `KAFKA_CLUSTERS_0_KAFKACONNECT_0_ADDRESS` | Адрес Kafka Connect |
| `KAFKA_CLUSTERS_0_KAFKACONNECT_0_USERNAME` | Basic auth username Kafka Connect |
| `KAFKA_CLUSTERS_0_KAFKACONNECT_0_PASSWORD` | Basic auth password Kafka Connect |
| `KAFKA_CLUSTERS_0_KAFKACONNECT_0_KEYSTORELOCATION` | JKS keystore Kafka Connect |
| `KAFKA_CLUSTERS_0_KAFKACONNECT_0_KEYSTOREPASSWORD` | Пароль keystore Kafka Connect |
| `KAFKA_CLUSTERS_0_KAFKACONNECT_0_TRUSTSTORELOCATION` | JKS truststore Kafka Connect |
| `KAFKA_CLUSTERS_0_KAFKACONNECT_0_TRUSTSTOREPASSWORD` | Пароль truststore Kafka Connect |
| `KAFKA_CLUSTERS_0_METRICS_SSL` | SSL для метрик: `true` / `false` |
| `KAFKA_CLUSTERS_0_METRICS_USERNAME` | Username для метрик |
| `KAFKA_CLUSTERS_0_METRICS_PASSWORD` | Password для метрик |
| `KAFKA_CLUSTERS_0_POLLING_THROTTLE_RATE` | Лимит трафика polling (bytes/sec). По умолчанию: 0 |
| `TOPIC_RECREATE_DELAY_SECONDS` | Задержка между удалением и созданием топика. По умолчанию: 1 |
| `TOPIC_RECREATE_MAXRETRIES` | Число попыток recreate. По умолчанию: 15 |

Дополнительные кластеры: `KAFKA_CLUSTERS_1_*`, `KAFKA_CLUSTERS_2_*` и т.д.

Пример YAML (`kafka-ui-api/src/main/resources/application-local.yml`):

```yaml
kafka:
  clusters:
    - name: local
      bootstrapServers: localhost:29091
      schemaRegistry: http://localhost:8085
      schemaRegistryAuth:
        username: username
        password: password
      metrics:
        port: 9997
        type: JMX
```

---

## Сборка образа

Из корня репозитория:

```bash
docker build \
  -f deploy/docker/Dockerfile \
  -t registry.example.com/kafka-ui:local-1 \
  .
docker push registry.example.com/kafka-ui:local-1
```

Backend-стадия тянет Maven-зависимости через зеркало в РФ (`https://mvn-mirror.gitverse.ru`). Артефакты Confluent завендорены в `deploy/docker/confluent-m2` (из РФ `packages.confluent.io` часто недоступен). Обновить кэш:

```bash
./deploy/docker/fetch-confluent-deps.sh
```

Другое зеркало Central:

```bash
docker build \
  --build-arg MAVEN_MIRROR_URL=https://maven-mirror.example.ru \
  -f deploy/docker/Dockerfile \
  -t registry.example.com/kafka-ui:local-1 \
  .
```

Dockerfile многостадийный:

1. frontend (`node:16.15.0`) — сборка React UI;
2. backend (`maven` + JDK 17) — Spring Boot jar;
3. runtime (`azul/zulu-openjdk-alpine:17`) — пользователь `kafkaui`, порт 8080.

Для kind / minikube без registry:

```bash
kind load docker-image kafka-ui-local:latest --name <cluster>
# или
minikube image load kafka-ui-local:latest
```

---

## Развёртывание в Docker Compose

Каталог: `deploy/docker/`

| Параметр | Значение |
|----------|----------|
| Образ | Собирается из `deploy/docker/Dockerfile` (`kafka-ui-local:latest`) |
| Порт | `8080:8080` |
| Auth | `AUTH_TYPE=LOGIN_FORM` |
| Пользователи | volume `kafka-ui-data` → `/etc/kafkaui/users.json` |
| Кластер | env `KAFKA_CLUSTERS_0_*` |

### Требования

- Docker Engine + Docker Compose v2;
- доступ с контейнера до Kafka bootstrap;
- свободный порт 8080 (или измените mapping).

### Запуск

```bash
cd deploy/docker
export KAFKA_UI_BOOTSTRAP_SERVERS=10.251.86.237:9092,10.251.86.238:9092
export KAFKA_UI_ADMIN_PASSWORD='ChangeMe-kafka-ui'
# опционально:
# export KAFKA_UI_CLUSTER_NAME=prod
# export KAFKA_UI_ADMIN_USERNAME=admin
# export KAFKA_UI_IMAGE=registry.example/kafka-ui:local-1

docker compose up --build -d
docker compose ps
docker compose logs -f kafka-ui
```

Откройте http://localhost:8080 → форма `/auth`.  
Первый вход: `KAFKA_UI_ADMIN_USERNAME` (по умолчанию `admin`) / `KAFKA_UI_ADMIN_PASSWORD`.

### Переменные compose

| Переменная | Обязательна | Описание | По умолчанию |
|------------|-------------|----------|--------------|
| `KAFKA_UI_BOOTSTRAP_SERVERS` | да | Bootstrap Kafka | — |
| `KAFKA_UI_ADMIN_PASSWORD` | да | Пароль первого `READ_WRITE` | — |
| `KAFKA_UI_ADMIN_USERNAME` | нет | Имя первого пользователя | `admin` |
| `KAFKA_UI_CLUSTER_NAME` | нет | Имя кластера в UI | `local` |
| `KAFKA_UI_IMAGE` | нет | Готовый образ вместо локальной сборки | `kafka-ui-local:latest` |

### Постоянные данные в Docker

Именованный volume `kafka-ui-data` → `/etc/kafkaui`.

| Команда | Volume |
|---------|--------|
| `docker compose down` | сохраняется |
| `docker compose down -v` | удаляется вместе с пользователями |
| повторный `up --build` | пользователи сохраняются |

Backup / restore:

```bash
docker compose cp kafka-ui:/etc/kafkaui/users.json ./users.json.backup
docker compose cp ./users.json.backup kafka-ui:/etc/kafkaui/users.json
docker compose restart kafka-ui
```

### Управление пользователями в UI

1. Войдите как администратор.
2. Меню **User accounts**.
3. Создайте пользователя с правом **Read** или **Read / write**.
4. При необходимости смените пароль кнопкой **Set password**.

### Типичные проблемы Docker

| Симптом | Что проверить |
|---------|----------------|
| Unset variable | Задайте `KAFKA_UI_BOOTSTRAP_SERVERS` и `KAFKA_UI_ADMIN_PASSWORD` |
| UI не открывается | `docker compose logs`, порт 8080 |
| Нет кластера | Доступность bootstrap из контейнера |
| Сбросились пользователи | Не использовали ли `down -v` |
| Пароль из env не применился | Volume уже содержит `users.json` — меняйте пароль в UI |

---

## Развёртывание в Kubernetes

Каталог манифестов: `deploy/k8s/manifests/kafka-ui/`

Рекомендуемый способ для production.

### Архитектура

В namespace `kafka-ui` разворачивается **один** инстанс.

```
                    ┌──────────────────────────────────────────────┐
                    │                 namespace: kafka-ui          │
                    │                                              │
  Internet/LAN ───► │  Ingress (kafka-ui.eisnot.ru)                │
                    │           │                                  │
                    │           ▼                                  │
                    │  Service kafka-ui :80                        │
                    │           │                                  │
                    │           ▼                                  │
                    │  Deployment kafka-ui (1 replica, Recreate)   │
                    │    envFrom: ConfigMap + Secret               │
                    │    volumeMount: /etc/kafkaui                 │
                    │           │                                  │
                    │           ▼                                  │
                    │  PVC kafka-ui-data  → users.json             │
                    └──────────────────────────────────────────────┘
                                      │
                                      ▼
                          Kafka brokers (prod)
```

| Компонент | Роль |
|-----------|------|
| ConfigMap `kafka-ui` | Brokers, `AUTH_TYPE`, путь к `users.json` |
| Secret `kafka-ui-auth` | Bootstrap-администратор (только для создания `users.json`) |
| PVC `kafka-ui-data` | Том с `users.json` |
| Deployment `kafka-ui` | Приложение |
| Service `kafka-ui` | ClusterIP, порт 80 → контейнер 8080 |
| Ingress `kafka-ui` | Внешний HTTP(S)-доступ |

Один URL обслуживает роли `READ` и `READ_WRITE`. Отдельный readonly-инстанс не нужен.

### Требования

**Кластер**

- Kubernetes ≥ 1.21;
- StorageClass с `ReadWriteOnce` (PVC по умолчанию 1Gi);
- Ingress Controller класса `nginx` (или смените `ingressClassName` в `04-ingress.yaml`);
- сеть от pod Kafka UI до Kafka brokers.

**Инструменты**

- `kubectl` с нужным контекстом;
- Docker / Buildah / Kaniko для сборки образа;
- доступ к container registry (или `imagePullPolicy: Never` / `IfNotPresent` на однонодовом кластере).

### Состав манифестов

| Файл | Kind | Назначение |
|------|------|------------|
| `00-namespace.yaml` | Namespace | namespace `kafka-ui` |
| `01-configmap.yaml` | ConfigMap | brokers, auth, путь к `users.json` |
| `01a-pvc.yaml` | PersistentVolumeClaim | том `kafka-ui-data` |
| `01b-secret.yaml` | Secret | bootstrap username/password |
| `02-deployment.yaml` | Deployment | pod, envFrom, PVC, probes, resources |
| `03-service.yaml` | Service | ClusterIP |
| `04-ingress.yaml` | Ingress | host `kafka-ui.eisnot.ru` |

Порядок apply по имени файлов совпадает с зависимостями.

### Настройка перед apply

#### ConfigMap — `01-configmap.yaml`

```yaml
data:
  KAFKA_CLUSTERS_0_NAME: prod
  KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: 10.251.86.237:9092,10.251.86.238:9092,10.251.86.239:9092
  AUTH_TYPE: LOGIN_FORM
  AUTH_LOCAL_USERS_FILE: /etc/kafkaui/users.json
```

Замените bootstrap-адреса. Pod должен иметь доступ к этим портам.

Дополнительный кластер:

```yaml
  KAFKA_CLUSTERS_1_NAME: staging
  KAFKA_CLUSTERS_1_BOOTSTRAPSERVERS: staging-kafka:9092
```

Опционально Schema Registry / Connect:

```yaml
  KAFKA_CLUSTERS_0_SCHEMAREGISTRY: http://schema-registry:8081
  KAFKA_CLUSTERS_0_KAFKACONNECT_0_NAME: connect
  KAFKA_CLUSTERS_0_KAFKACONNECT_0_ADDRESS: http://kafka-connect:8083
```

#### Secret — `01b-secret.yaml`

```yaml
stringData:
  AUTH_LOCAL_BOOTSTRAP_USERNAME: admin
  AUTH_LOCAL_BOOTSTRAP_PASSWORD: "ChangeMe-kafka-ui"
```

**Смените пароль до первого apply в prod.**  
Значения используются только если на PVC ещё нет `users.json`. После создания файла
пользователи живут на томе; правка Secret сама по себе пароль не меняет.

#### PVC — `01a-pvc.yaml`

```yaml
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
  # storageClassName: your-storage-class
```

#### Deployment — `02-deployment.yaml`

Укажите свой образ:

```yaml
image: registry.example.com/kafka-ui:local-1
imagePullPolicy: IfNotPresent
```

Ресурсы по умолчанию: requests `100m/256Mi`, limits `1 CPU / 1Gi`.  
Стратегия: `Recreate`, `replicas: 1`, `fsGroup: 1000`.

#### Ingress — `04-ingress.yaml`

```yaml
rules:
  - host: kafka-ui.eisnot.ru
```

Замените DNS-имя. TLS см. ниже.

### Первичное развёртывание

```bash
# 1. Собрать и опубликовать образ
docker build -f deploy/docker/Dockerfile -t registry.example.com/kafka-ui:local-1 .
docker push registry.example.com/kafka-ui:local-1
# указать image в 02-deployment.yaml

# 2. Проверить контекст
kubectl config current-context
kubectl get nodes
kubectl get sc

# 3. Apply
kubectl apply -f deploy/k8s/manifests/kafka-ui/

# или пошагово:
# kubectl apply -f deploy/k8s/manifests/kafka-ui/00-namespace.yaml
# ... 01-configmap, 01a-pvc, 01b-secret, 02-deployment, 03-service, 04-ingress

# 4. Дождаться готовности
kubectl -n kafka-ui rollout status deploy/kafka-ui --timeout=180s
kubectl -n kafka-ui get po,pvc,svc,ingress
```

Ожидаемо:

| Объект | Статус |
|--------|--------|
| Pod `kafka-ui-…` | `1/1 Running` |
| PVC `kafka-ui-data` | `Bound` |
| Service `kafka-ui` | ClusterIP |
| Ingress `kafka-ui` | ADDRESS заполнен |

Проверка health:

```bash
kubectl -n kafka-ui port-forward svc/kafka-ui 8080:80
curl -sS http://127.0.0.1:8080/actuator/health
```

Логи первого старта:

```bash
kubectl -n kafka-ui logs -l app.kubernetes.io/component=admin --tail=100
```

### Первый вход и пользователи

1. Откройте `http://kafka-ui.eisnot.ru` (или port-forward).
2. Форма логина: `/auth`.
3. Логин/пароль из Secret `kafka-ui-auth` (по умолчанию в манифесте: `admin` / `ChangeMe-kafka-ui`).
4. Меню **User accounts** → создайте пользователя `READ` (viewer) и/или второго `READ_WRITE`.
5. Выйдите и проверьте вход viewer: UI на просмотр, изменение Kafka API → 403.

### Постоянные данные в Kubernetes

| Данные | Место | recreate pod | delete PVC |
|--------|-------|--------------|------------|
| `users.json` | PVC `kafka-ui-data` → `/etc/kafkaui/users.json` | сохраняется | теряется |
| Brokers / auth type | ConfigMap `kafka-ui` | сохраняется | сохраняется |
| Bootstrap credentials | Secret `kafka-ui-auth` | сохраняется | сохраняется |
| In-memory кэш | память pod | теряется | — |

Том монтируется на весь `/etc/kafkaui` — стандартный каталог данных Kafka UI.
После возможного merge Configuration Wizard туда же можно класть
`dynamic_config.yaml` и `uploads/`.

**Нельзя:**

- `kubectl delete -f deploy/k8s/manifests/kafka-ui/` без backup — удалит PVC;
- менять `AUTH_LOCAL_USERS_FILE` вне PVC без переноса файла;
- поднимать `replicas > 1` на RWO без RWX-тома.

### Изменение конфигурации Kafka

Список серверов в этой ветке меняется только через ConfigMap:

```bash
kubectl apply -f deploy/k8s/manifests/kafka-ui/01-configmap.yaml
kubectl -n kafka-ui rollout restart deploy/kafka-ui
kubectl -n kafka-ui rollout status deploy/kafka-ui
```

`users.json` при этом не затрагивается.

### Смена паролей

**Существующий пользователь:** UI **User accounts** → New password → **Set password**,  
или `PUT /api/auth/users/{username}`:

```json
{"password":"new-strong-password","role":"READ_WRITE"}
```

**Обновление Secret** влияет только на «чистый» PVC без `users.json`:

```bash
kubectl -n kafka-ui create secret generic kafka-ui-auth \
  --from-literal=AUTH_LOCAL_BOOTSTRAP_USERNAME=admin \
  --from-literal=AUTH_LOCAL_BOOTSTRAP_PASSWORD='НОВЫЙ_ПАРОЛЬ' \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n kafka-ui rollout restart deploy/kafka-ui
```

Сброс пользователей к bootstrap: backup → `replicas: 0` → удалить `users.json`
с тома или пересоздать PVC → снова `replicas: 1`.

### Обновление приложения

```bash
docker build -f deploy/docker/Dockerfile -t registry.example.com/kafka-ui:local-2 .
docker push registry.example.com/kafka-ui:local-2
kubectl -n kafka-ui set image deploy/kafka-ui kafka-ui=registry.example.com/kafka-ui:local-2
kubectl -n kafka-ui rollout status deploy/kafka-ui
```

Стратегия `Recreate` нужна для RWO PVC. Откат:

```bash
kubectl -n kafka-ui rollout undo deploy/kafka-ui
```

### Масштабирование

Конфигурация рассчитана на **1 replica**. Причины: RWO PVC и in-memory сессии.
Для нескольких реплик нужны RWX StorageClass, sticky sessions и учёт гонок записи
в `users.json`. Практическая рекомендация на этом форке: оставьте 1 replica.

### Ingress, DNS и TLS

DNS:

```
kafka-ui.eisnot.ru  →  IP Ingress Controller / LoadBalancer
```

TLS:

```bash
kubectl -n kafka-ui create secret tls kafka-ui-tls \
  --cert=fullchain.pem \
  --key=privkey.pem
```

В `04-ingress.yaml` раскомментируйте:

```yaml
  tls:
    - hosts:
        - kafka-ui.eisnot.ru
      secretName: kafka-ui-tls
```

Аннотации nginx уже заданы: `proxy-body-size: 50m`, длинные timeout для SSE,
cookie affinity.

### Backup и restore

```bash
POD=$(kubectl -n kafka-ui get pod -l app.kubernetes.io/component=admin -o jsonpath='{.items[0].metadata.name}')
kubectl -n kafka-ui cp "$POD:/etc/kafkaui/users.json" ./users.json.backup
```

Восстановление:

```bash
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=1
POD=$(kubectl -n kafka-ui get pod -l app.kubernetes.io/component=admin -o jsonpath='{.items[0].metadata.name}')
kubectl -n kafka-ui cp ./users.json.backup "$POD:/etc/kafkaui/users.json"
kubectl -n kafka-ui rollout restart deploy/kafka-ui
```

При наличии CSI VolumeSnapshot можно снимать snapshot PVC `kafka-ui-data`.

### Остановка, запуск, удаление

```bash
# стоп (данные сохранить)
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=0

# старт
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=1

# удалить приложение, сохранив пользователей
kubectl -n kafka-ui delete deploy,svc,ingress kafka-ui

# полное удаление, включая PVC и пользователей
kubectl delete -f deploy/k8s/manifests/kafka-ui/
# или: kubectl delete ns kafka-ui
```

### Безопасность

| Практика | Рекомендация |
|----------|--------------|
| Bootstrap-пароль | Сменить до prod-apply; не коммитить реальные пароли |
| Secret в Git | Sealed Secrets / SOPS / External Secrets |
| TLS | Включить на Ingress |
| Сеть | NetworkPolicy: egress только к Kafka / SR / Connect |
| PVC | Не удалять без backup; бэкапы по расписанию |
| Роли приложения | Запасной `READ_WRITE`; зрители — только `READ` |

### Диагностика

```bash
kubectl -n kafka-ui get all,pvc,ingress,cm,secret
kubectl -n kafka-ui describe deploy kafka-ui
kubectl -n kafka-ui describe pvc kafka-ui-data
kubectl -n kafka-ui logs -l app.kubernetes.io/component=admin --tail=200 -f
kubectl -n kafka-ui get events --sort-by=.lastTimestamp | tail -50

POD=$(kubectl -n kafka-ui get pod -l app.kubernetes.io/component=admin -o jsonpath='{.items[0].metadata.name}')
kubectl -n kafka-ui exec "$POD" -- ls -la /etc/kafkaui
```

### Частые ошибки Kubernetes

| Симптом | Причина | Решение |
|---------|---------|---------|
| `ImagePullBackOff` | Нет образа в registry | Собрать, push, поправить `image` |
| `CrashLoopBackOff`, ошибка записи `/etc/kafkaui` | Права PVC | Проверить `fsGroup: 1000` |
| PVC `Pending` | Нет StorageClass / квоты | `kubectl get sc`, указать `storageClassName` |
| 401 / вечный логин | Другой `users.json` или пароль | Secret только для чистого PVC; иначе UI |
| `READ` не видит User accounts | Ожидаемо | Только для `READ_WRITE` |
| Secret apply не сменил пароль | `users.json` уже есть | Менять через UI / API |
| Два pod + том | replicas > 1 на RWO | Вернуть `replicas: 1` |
| Ingress без ADDRESS | Нет controller | Установить nginx ingress |
| Пустой кластер в UI | Неверный bootstrap / сеть | ConfigMap + connectivity |

### Чеклист prod

- [ ] Собран и опубликован свой образ (не upstream)
- [ ] В `02-deployment.yaml` правильный `image` / тег
- [ ] В ConfigMap реальные bootstrap-серверы
- [ ] Bootstrap-пароль изменён с `ChangeMe-…`
- [ ] PVC в статусе `Bound`, понятен backup
- [ ] DNS → Ingress, TLS желателен
- [ ] Есть запасной `READ_WRITE` и пользователи `READ`
- [ ] Проверен вход обеих ролей
- [ ] Старый readonly Deployment удалён (если был)

### Шпаргалка kubectl

```bash
kubectl apply -f deploy/k8s/manifests/kafka-ui/
kubectl -n kafka-ui rollout status deploy/kafka-ui
kubectl -n kafka-ui get po,pvc,svc,ingress
kubectl -n kafka-ui logs -l app.kubernetes.io/component=admin --tail=100
kubectl apply -f deploy/k8s/manifests/kafka-ui/01-configmap.yaml
kubectl -n kafka-ui rollout restart deploy/kafka-ui
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=0
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=1
```

---

## Helm

Чарт: `charts/kafka-ui/`. Быстрый старт upstream: `helm_chart.md`.

Параметры persistence этого форка:

| Values | Описание |
|--------|----------|
| `persistence.enabled` | Создать PVC и смонтировать в `/etc/kafkaui` |
| `persistence.size` | Размер тома (по умолчанию `1Gi`) |
| `persistence.storageClass` | StorageClass (пусто = default) |
| `persistence.existingClaim` | Уже существующий PVC |
| `persistence.mountPath` | Путь монтирования (по умолчанию `/etc/kafkaui`) |

Пример:

```bash
helm upgrade --install kafka-ui charts/kafka-ui \
  --namespace kafka-ui --create-namespace \
  --set image.repository=registry.example/kafka-ui \
  --set image.tag=local \
  --set persistence.enabled=true \
  --set envs.config.AUTH_TYPE=LOGIN_FORM \
  --set envs.config.AUTH_LOCAL_USERS_FILE=/etc/kafkaui/users.json \
  --set envs.config.KAFKA_CLUSTERS_0_NAME=prod \
  --set envs.config.KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=kafka:9092 \
  --set envs.secret.AUTH_LOCAL_BOOTSTRAP_USERNAME=admin \
  --set envs.secret.AUTH_LOCAL_BOOTSTRAP_PASSWORD='ChangeMe-kafka-ui'
```

Нужен образ этой ветки, не `provectuslabs/kafka-ui`.

Проверка:

```bash
kubectl port-forward svc/kafka-ui 8080:80
```

---

## Миграция со схемы admin + readonly

Раньше:

- `kafka-ui` — полный доступ;
- `kafka-ui-readonly` — отдельный инстанс с `KAFKA_CLUSTERS_0_READONLY=true`.

Новая схема:

1. Соберите и опубликуйте образ этой ветки.
2. Разверните один Deployment с PVC.
3. Войдите как bootstrap-администратор.
4. Создайте пользователя с ролью `READ` вместо второго инстанса.
5. Удалите старые объекты:

```bash
kubectl -n kafka-ui delete deploy kafka-ui-readonly --ignore-not-found
kubectl -n kafka-ui delete svc kafka-ui-readonly --ignore-not-found
kubectl -n kafka-ui delete configmap kafka-ui-readonly --ignore-not-found
kubectl -n kafka-ui delete secret kafka-ui-auth-readonly --ignore-not-found
```

6. Уберите второй Ingress-host (`kafka-ui-ro.…`), если он остался.

Пароли из старых Secret автоматически не импортируются.

---

## Сборка и запуск из исходников

### С Docker

- Prerequisites: [documentation/project/contributing/prerequisites.md](documentation/project/contributing/prerequisites.md)
- Building: [documentation/project/contributing/building.md](documentation/project/contributing/building.md)

### Без Docker

- Prerequisites: [documentation/project/contributing/prerequisites.md](documentation/project/contributing/prerequisites.md)
- Быстрый запуск: [building-and-running-without-docker.md](documentation/project/contributing/building-and-running-without-docker.md#run_without_docker_quickly)
- Сборка и запуск: [building-and-running-without-docker.md](documentation/project/contributing/building-and-running-without-docker.md#build_and_run_without_docker)

Примеры docker-compose стеков: [documentation/compose/DOCKER_COMPOSE.md](documentation/compose/DOCKER_COMPOSE.md).

---

## Health-пробы

- Liveness / readiness: `/actuator/health`
- Info (build info): `/actuator/info`

---

## Дополнительные гайды

- [Документация для Confluence (эксплуатация)](documentation/confluence/Kafka-UI-Operations.md)
- [SSO](documentation/guides/SSO.md)
- [AWS IAM](documentation/guides/AWS_IAM.md)
- [Docker Compose примеры](documentation/compose/DOCKER_COMPOSE.md)
- [Подключение к secure broker](documentation/guides/SECURE_BROKER.md)
- [Сериализация / плагины serde](documentation/guides/Serialization.md)
- [Helm quick start](helm_chart.md)
