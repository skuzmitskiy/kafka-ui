# Kafka UI в Docker Compose

Развёртывание локального/стендового инстанса с несколькими учётными записями
и именованным volume для сохранения пользователей.

Обзор: [deploy/README.md](../README.md)  
Kubernetes (prod): [../k8s/manifests/kafka-ui/README.md](../k8s/manifests/kafka-ui/README.md)

---

## 1. Что поднимается

Файл `docker-compose.yaml` описывает один сервис `kafka-ui`:

| Параметр | Значение |
|----------|----------|
| Образ | Собирается из `deploy/docker/Dockerfile` (`kafka-ui-local:latest`) |
| Порт | `8080:8080` |
| Auth | `AUTH_TYPE=LOGIN_FORM` |
| Пользователи | volume `kafka-ui-data` → `/etc/kafkaui/users.json` |
| Кластер | env `KAFKA_CLUSTERS_0_*` |

---

## 2. Требования

- Docker Engine + Docker Compose v2;
- доступ с контейнера до Kafka bootstrap-серверов;
- свободный порт 8080 на хосте (или измените mapping).

---

## 3. Запуск

Из каталога `deploy/docker`:

```bash
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

Первый вход: username из `KAFKA_UI_ADMIN_USERNAME` (по умолчанию `admin`),
password из `KAFKA_UI_ADMIN_PASSWORD`.

---

## 4. Переменные окружения

| Переменная | Обязательна | Описание | По умолчанию |
|------------|-------------|----------|--------------|
| `KAFKA_UI_BOOTSTRAP_SERVERS` | да | Bootstrap Kafka | — |
| `KAFKA_UI_ADMIN_PASSWORD` | да | Пароль первого `READ_WRITE` | — |
| `KAFKA_UI_ADMIN_USERNAME` | нет | Имя первого пользователя | `admin` |
| `KAFKA_UI_CLUSTER_NAME` | нет | Имя кластера в UI | `local` |
| `KAFKA_UI_IMAGE` | нет | Готовый образ вместо локальной сборки | `kafka-ui-local:latest` |

Внутри контейнера дополнительно задаются:

- `AUTH_TYPE=LOGIN_FORM`
- `AUTH_LOCAL_USERS_FILE=/etc/kafkaui/users.json`
- `AUTH_LOCAL_BOOTSTRAP_*` из переменных выше

---

## 5. Постоянные данные

Именованный volume `kafka-ui-data` смонтирован в `/etc/kafkaui`.

| Команда | Volume |
|---------|--------|
| `docker compose down` | **сохраняется** |
| `docker compose down -v` | **удаляется** вместе с пользователями |
| `docker compose up --build -d` (повторно) | пользователи сохраняются |

Список Kafka-серверов задаётся env при старте compose и переживает
пересоздание контейнера, пока вы не измените переменные / файл compose.

Просмотр volume:

```bash
docker volume inspect kafka-ui-data
docker compose exec kafka-ui ls -la /etc/kafkaui
```

Backup:

```bash
docker compose cp kafka-ui:/etc/kafkaui/users.json ./users.json.backup
```

Restore:

```bash
docker compose cp ./users.json.backup kafka-ui:/etc/kafkaui/users.json
docker compose restart kafka-ui
```

---

## 6. Управление пользователями

После входа администратора:

1. Меню **User accounts**.
2. Создайте пользователя с правом **Read** или **Read / write**.
3. При необходимости смените пароль кнопкой **Set password**.

Роли:

- `READ` — только просмотр;
- `READ_WRITE` — изменение Kafka и управление учётками.

---

## 7. Сборка образа отдельно

Из корня репозитория:

```bash
docker build -f deploy/docker/Dockerfile -t kafka-ui-local:latest .
```

Затем:

```bash
export KAFKA_UI_IMAGE=kafka-ui-local:latest
export KAFKA_UI_BOOTSTRAP_SERVERS=kafka:9092
export KAFKA_UI_ADMIN_PASSWORD='ChangeMe-kafka-ui'
docker compose up -d   # без --build, если образ уже есть
```

---

## 8. Остановка и удаление

```bash
# остановить контейнер, volume сохранить
docker compose down

# удалить всё, включая пользователей
docker compose down -v
```

---

## 9. Ограничения этой ветки

Ветка `dev` **не содержит** Configuration Wizard из upstream `v0.7.2`.
Runtime-редактирование списка серверов через UI недоступно: меняйте
`KAFKA_UI_BOOTSTRAP_SERVERS` / compose и перезапускайте сервис.

После синхронизации с `origin/master` тот же volume `/etc/kafkaui` подойдёт
для `dynamic_config.yaml` и каталога `uploads/`.

---

## 10. Типичные проблемы

| Симптом | Что проверить |
|---------|----------------|
| Compose ругается на unset variable | Задайте `KAFKA_UI_BOOTSTRAP_SERVERS` и `KAFKA_UI_ADMIN_PASSWORD` |
| Не открывается UI | `docker compose logs`, порт 8080, firewall |
| Не видно кластера | Доступность bootstrap из контейнера (`docker compose exec`) |
| Сбросились пользователи | Не использовали ли `down -v` |
| Пароль из env не применился | Volume уже содержит `users.json` — меняйте пароль в UI |
