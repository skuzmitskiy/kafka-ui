# Развёртывание Kafka UI (локальные пользователи и постоянные данные)

Этот каталог описывает деплой форка Kafka UI с поддержкой:

- нескольких локальных учётных записей;
- ролей **чтение** (`READ`) и **чтение/запись** (`READ_WRITE`);
- сохранения пользователей между пересозданием контейнера / pod;
- сохранения списка Kafka-кластеров через ConfigMap (Kubernetes) или environment (Docker).

| Способ | Документация |
|--------|--------------|
| Kubernetes (рекомендуется для prod) | [deploy/k8s/manifests/kafka-ui/README.md](k8s/manifests/kafka-ui/README.md) |
| Docker Compose | [deploy/docker/README.md](docker/README.md) |
| Helm | [charts/kafka-ui](../charts/kafka-ui) + секция ниже |

---

## 1. Что изменилось относительно upstream

### 1.1. Учётные записи

В upstream `LOGIN_FORM` поддерживает **одного** пользователя на инстанс
(`SPRING_SECURITY_USER_NAME` / `SPRING_SECURITY_USER_PASSWORD`). Поэтому раньше
для readonly делали второй Deployment.

В этой сборке при `AUTH_TYPE=LOGIN_FORM`:

- пользователи хранятся в файле `users.json` (по умолчанию `/etc/kafkaui/users.json`);
- пароли записываются только как **BCrypt-хеши**;
- роли:
  - `READ` — просмотр Kafka-данных (GET/HEAD/OPTIONS);
  - `READ_WRITE` — изменение Kafka-данных и управление учётными записями;
- первый пользователь создаётся при старте из bootstrap-переменных, если файла ещё нет;
- дальнейшее управление — через UI **User accounts** (`/ui/users`) или API `/api/auth/*`.

### 1.2. Постоянные данные

| Данные | Где хранятся | Переживают redeploy? |
|--------|--------------|----------------------|
| Учётные записи | volume / PVC → `/etc/kafkaui/users.json` | Да, пока том не удалён |
| Список Kafka-кластеров | ConfigMap (K8s) или env (Docker) | Да |
| Пароли bootstrap | Secret (K8s) / env (Docker) | Используются только при первом создании `users.json` |
| Runtime Configuration Wizard | — | **Нет в этой ветке `dev`** |

> Ветка `dev` не содержит Configuration Wizard из upstream `v0.7.2`.
> Переменная `DYNAMIC_CONFIG_ENABLED` для образа, собранного из этой ветки,
> не поддерживается. Список серверов задаётся через ConfigMap / env.

### 1.3. Образ

Upstream-образ `provectuslabs/kafka-ui:v0.7.2` **не содержит** локального
multi-user хранилища. Нужен образ, собранный из этой ветки:

```bash
docker build -f deploy/docker/Dockerfile -t kafka-ui-local:latest .
```

---

## 2. Роли и поведение API

| HTTP-метод | `READ` | `READ_WRITE` |
|------------|--------|--------------|
| GET / HEAD / OPTIONS | разрешено | разрешено |
| POST / PUT / PATCH / DELETE к Kafka API | запрещено | разрешено |
| `/api/auth/me` | разрешено | разрешено |
| `/api/auth/users` (список / создание / изменение / удаление) | запрещено | разрешено |

Страница **User accounts** в меню видна только пользователям с ролью `READ_WRITE`.

Ограничения безопасности хранилища:

- нельзя удалить собственную учётную запись;
- нельзя удалить или понизить последнего пользователя с `READ_WRITE`;
- имя пользователя: 3–64 символа (`A-Za-z0-9._@-`);
- пароль: минимум 8 символов.

---

## 3. Переменные окружения

| Переменная | Описание | По умолчанию |
|------------|----------|--------------|
| `AUTH_TYPE` | Тип аутентификации. Для локальных пользователей — `LOGIN_FORM` | `DISABLED` |
| `AUTH_LOCAL_USERS_FILE` | Путь к файлу пользователей | `/etc/kafkaui/users.json` |
| `AUTH_LOCAL_BOOTSTRAP_USERNAME` | Имя первого администратора | `admin` (или `SPRING_SECURITY_USER_NAME`) |
| `AUTH_LOCAL_BOOTSTRAP_PASSWORD` | Пароль первого администратора | обязателен при первом старте |
| `KAFKA_CLUSTERS_0_NAME` | Имя первого кластера | — |
| `KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS` | Bootstrap-серверы Kafka | — |
| `KAFKA_CLUSTERS_0_READONLY` | Сделать **весь** кластер read-only (независимо от роли пользователя) | `false` |

Дополнительные кластеры задаются индексом: `KAFKA_CLUSTERS_1_*`, `KAFKA_CLUSTERS_2_*` и т.д.

---

## 4. API управления пользователями

Базовый путь: `/api/auth`. Требуется сессия после form-login (`/auth`).

| Метод | Путь | Кто | Описание |
|-------|------|-----|----------|
| `GET` | `/api/auth/me` | любой авторизованный | текущий пользователь и роль |
| `GET` | `/api/auth/users` | `READ_WRITE` | список пользователей |
| `POST` | `/api/auth/users` | `READ_WRITE` | создать пользователя |
| `PUT` | `/api/auth/users/{username}` | `READ_WRITE` | изменить роль / пароль |
| `DELETE` | `/api/auth/users/{username}` | `READ_WRITE` | удалить пользователя |

Пример создания:

```bash
curl -X POST 'https://kafka-ui.example/api/auth/users' \
  -H 'Content-Type: application/json' \
  -b cookies.txt \
  -d '{"username":"viewer","password":"viewer-password","role":"READ"}'
```

Тело ответа (без пароля):

```json
{"username":"viewer","role":"READ"}
```

---

## 5. Быстрый старт

### Docker

```bash
export KAFKA_UI_BOOTSTRAP_SERVERS=10.251.86.237:9092
export KAFKA_UI_ADMIN_PASSWORD='ChangeMe-kafka-ui'
cd deploy/docker
docker compose up --build -d
```

Подробности: [docker/README.md](docker/README.md).

### Kubernetes

```bash
# 1. Собрать и опубликовать образ
docker build -f deploy/docker/Dockerfile -t registry.example/kafka-ui:local .
docker push registry.example/kafka-ui:local

# 2. Указать образ в 02-deployment.yaml, затем:
kubectl apply -f deploy/k8s/manifests/kafka-ui/
```

Подробности: [k8s/manifests/kafka-ui/README.md](k8s/manifests/kafka-ui/README.md).

### Helm

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

`persistence.enabled=true` монтирует PVC в `/etc/kafkaui` и сохраняет `users.json`.

---

## 6. Миграция со старой схемы «admin + readonly»

Раньше использовались два Deployment:

- `kafka-ui` — полный доступ;
- `kafka-ui-readonly` — отдельный инстанс с `KAFKA_CLUSTERS_0_READONLY=true`.

Новая схема:

1. Соберите и опубликуйте образ этой ветки.
2. Разверните один Deployment с PVC.
3. Войдите как bootstrap-администратор.
4. Создайте пользователя с ролью `READ` вместо второго инстанса.
5. Удалите старые объекты `kafka-ui-readonly*` и второй Ingress-host.

Пароли из старых Secret не переносятся автоматически — задайте их заново при создании пользователей.
