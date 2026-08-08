# Kafka UI — эксплуатация (авторизация, роли, Kubernetes)

| Поле | Значение |
|------|----------|
| Продукт | UI for Apache Kafka (форк с multi-user auth) |
| Namespace | `kafka-ui` |
| URL | `https://kafka-ui.eisnot.ru` (или HTTP по Ingress) |
| Образ (пример) | `repo.fciit.ru:9445/kafka-ui:1.0.0` |
| Репозиторий манифестов | `deploy/k8s/manifests/kafka-ui/` |
| Репозиторий Docker | `deploy/docker/` |

## Как импортировать в Confluence

1. Создайте страницу в нужном пространстве.
2. Вставьте содержимое этого файла одним из способов:
   - **Confluence Cloud**: `/markdown` → вставить текст → Publish;
   - **Import**: *••• → Import* → Markdown (если доступно в вашей версии);
   - **Копирование**: вставьте в редактор; таблицы и code blocks обычно сохраняются.
3. После импорта проверьте таблицы и блоки кода; при необходимости оберните важные предупреждения в макрос *Info* / *Warning*.
4. Картинки из upstream README сюда не включены — при необходимости добавьте скриншоты UI отдельно.

---

## 1. Назначение

Веб-интерфейс для мониторинга и управления кластерами Apache Kafka.

В этой сборке дополнительно:

- несколько локальных учётных записей;
- роли **READ** и **READ_WRITE**;
- пользователи на PVC / Docker volume (`users.json`);
- список Kafka-кластеров через ConfigMap / env;
- **один** Deployment вместо схемы admin + readonly.

> Upstream-образ `provectuslabs/kafka-ui` **не содержит** multi-user хранилище. Нужен образ, собранный из этой ветки (`deploy/docker/Dockerfile`).

---

## 2. Роли и доступ

| Роль | Возможности |
|------|-------------|
| `READ` | Просмотр (GET/HEAD/OPTIONS). Нет страницы User accounts. Запись в Kafka API запрещена |
| `READ_WRITE` | Полный доступ к Kafka API + управление пользователями |

| HTTP | `READ` | `READ_WRITE` |
|------|--------|--------------|
| GET / HEAD / OPTIONS | да | да |
| POST / PUT / PATCH / DELETE (Kafka API) | нет | да |
| `/api/auth/me` | да | да |
| `/api/auth/users` | нет | да |

Ограничения:

- нельзя удалить свою учётную запись;
- нельзя удалить / понизить последнего `READ_WRITE`;
- username: 3–64 символа (`A-Za-z0-9._@-`);
- пароль: минимум 8 символов.

### API пользователей

Базовый путь: `/api/auth` (нужна сессия после `/auth`).

| Метод | Путь | Кто | Описание |
|-------|------|-----|----------|
| GET | `/api/auth/me` | любой | текущий пользователь |
| GET | `/api/auth/users` | `READ_WRITE` | список |
| POST | `/api/auth/users` | `READ_WRITE` | создать |
| PUT | `/api/auth/users/{username}` | `READ_WRITE` | роль / пароль |
| DELETE | `/api/auth/users/{username}` | `READ_WRITE` | удалить |

Пример создания viewer:

```bash
curl -X POST 'https://kafka-ui.eisnot.ru/api/auth/users' \
  -H 'Content-Type: application/json' \
  -b cookies.txt \
  -d '{"username":"viewer","password":"viewer-password","role":"READ"}'
```

---

## 3. Постоянные данные

| Данные | Где | Redeploy pod | Delete PVC / `down -v` |
|--------|-----|--------------|-------------------------|
| Учётные записи (`users.json`) | PVC / volume → `/etc/kafkaui/users.json` | сохраняются | теряются |
| Список Kafka-кластеров (ConfigMap / env) | ConfigMap / env | сохраняется | сохраняется (пока есть ConfigMap) |
| Список Kafka-кластеров (Configuration Wizard) | PVC / volume → `/etc/kafkaui/dynamic_config.yaml` | сохраняются | теряются |
| Bootstrap-пароль | Secret / env | только при **первом** создании `users.json` | — |

> В этой ветке Configuration Wizard (кнопка **Configure**/«Configure new cluster» в UI) **доступен**, когда задана переменная `DYNAMIC_CONFIG_ENABLED=true`. Кнопка и связанные страницы видны только пользователям с ролью `READ_WRITE` (см. `LocalUserRole`); пользователи с ролью `READ` не могут добавлять/редактировать кластеры через UI. Добавленные через wizard кластеры сохраняются в файле `/etc/kafkaui/dynamic_config.yaml` на том же PVC, что и `users.json`, поэтому переживают redeploy пода, но теряются при удалении PVC (`down -v` в docker compose). Кластеры, заданные через ConfigMap / env (`KAFKA_CLUSTERS_0_*`), продолжают работать как раньше и не зависят от `DYNAMIC_CONFIG_ENABLED`.

---

## 4. Переменные окружения (ключевые)

### Auth

| Переменная | Описание | По умолчанию |
|------------|----------|--------------|
| `AUTH_TYPE` | Для локальных пользователей: `LOGIN_FORM` | `DISABLED` |
| `AUTH_LOCAL_USERS_FILE` | Файл пользователей | `/etc/kafkaui/users.json` |
| `AUTH_LOCAL_BOOTSTRAP_USERNAME` | Первый администратор | `admin` |
| `AUTH_LOCAL_BOOTSTRAP_PASSWORD` | Пароль первого администратора | обязателен при первом старте |

### Kafka

| Переменная | Описание |
|------------|----------|
| `KAFKA_CLUSTERS_0_NAME` | Имя кластера в UI |
| `KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS` | Bootstrap-серверы |
| `KAFKA_CLUSTERS_0_SCHEMAREGISTRY` | Schema Registry (опционально) |
| `KAFKA_CLUSTERS_0_KAFKACONNECT_0_NAME` / `_ADDRESS` | Kafka Connect (опционально) |
| `KAFKA_CLUSTERS_0_READONLY` | Read-only **всего кластера** (независимо от роли пользователя) |

Дополнительные кластеры: `KAFKA_CLUSTERS_1_*`, `KAFKA_CLUSTERS_2_*`, …

---

## 5. Сборка образа

```bash
# из корня репозитория
docker build \
  -f deploy/docker/Dockerfile \
  -t repo.fciit.ru:9445/kafka-ui:1.0.0 \
  .

docker push repo.fciit.ru:9445/kafka-ui:1.0.0
```

Особенности сборки:

- Maven Central — через зеркало РФ `https://mvn-mirror.gitverse.ru` (override: `--build-arg MAVEN_MIRROR_URL=...`);
- артефакты Confluent завендорены в `deploy/docker/confluent-m2` (из РФ `packages.confluent.io` часто недоступен);
- обновление Confluent-кэша: `./deploy/docker/fetch-confluent-deps.sh`;
- UI собирается в отдельной Docker-стадии; в Maven передаётся `-DskipUIBuild=true`.

---

## 6. Docker Compose (стенд / локально)

Каталог: `deploy/docker/`.

```bash
cd deploy/docker
export KAFKA_UI_BOOTSTRAP_SERVERS=10.251.86.237:9092,10.251.86.238:9092
export KAFKA_UI_ADMIN_PASSWORD='ChangeMe-kafka-ui'
# опционально:
# export KAFKA_UI_IMAGE=repo.fciit.ru:9445/kafka-ui:1.0.0

docker compose up --build -d
docker compose ps
docker compose logs -f kafka-ui
```

| Переменная | Обязательна | Описание |
|------------|-------------|----------|
| `KAFKA_UI_BOOTSTRAP_SERVERS` | да | Bootstrap Kafka |
| `KAFKA_UI_ADMIN_PASSWORD` | да | Пароль первого `READ_WRITE` |
| `KAFKA_UI_ADMIN_USERNAME` | нет | по умолчанию `admin` |
| `KAFKA_UI_IMAGE` | нет | готовый образ вместо локальной сборки |

Пользователи: volume `kafka-ui-data`.  
`docker compose down` — сохраняет; `docker compose down -v` — удаляет пользователей.

---

## 7. Kubernetes — архитектура

Namespace: `kafka-ui`. Один инстанс обслуживает обе роли.

| Компонент | Роль |
|-----------|------|
| ConfigMap `kafka-ui` | Brokers, `AUTH_TYPE`, путь к `users.json` |
| Secret `kafka-ui-auth` | Bootstrap admin (только если нет `users.json`) |
| Secret `regcred` | Креды pull из private registry |
| PVC `kafka-ui-data` | `users.json` |
| Deployment `kafka-ui` | Приложение, 1 replica, стратегия Recreate |
| Service `kafka-ui` | ClusterIP 80 → 8080 |
| Ingress `kafka-ui` | Хост `kafka-ui.eisnot.ru` |

Манифесты: `deploy/k8s/manifests/kafka-ui/`.

| Файл | Kind |
|------|------|
| `00-namespace.yaml` | Namespace |
| `01-configmap.yaml` | ConfigMap |
| `01a-pvc.yaml` | PVC |
| `01b-secret.yaml` | Secret bootstrap |
| `02-deployment.yaml` | Deployment |
| `03-service.yaml` | Service |
| `04-ingress.yaml` | Ingress |

---

## 8. Первичное развёртывание в Kubernetes

### 8.1. Подготовка манифестов

ConfigMap (`01-configmap.yaml`):

```yaml
data:
  KAFKA_CLUSTERS_0_NAME: prod
  KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: 10.251.86.237:9092,10.251.86.238:9092,10.251.86.239:9092
  AUTH_TYPE: LOGIN_FORM
  AUTH_LOCAL_USERS_FILE: /etc/kafkaui/users.json
```

Secret (`01b-secret.yaml`) — сменить пароль до prod:

```yaml
stringData:
  AUTH_LOCAL_BOOTSTRAP_USERNAME: admin
  AUTH_LOCAL_BOOTSTRAP_PASSWORD: "ChangeMe-kafka-ui"
```

Deployment — указать образ и pull secret (см. §9).

### 8.2. Apply

```bash
kubectl apply -f deploy/k8s/manifests/kafka-ui/
kubectl -n kafka-ui rollout status deploy/kafka-ui --timeout=180s
kubectl -n kafka-ui get po,pvc,svc,ingress
```

Ожидаемо: Pod `1/1 Running`, PVC `Bound`, Ingress с ADDRESS.

### 8.3. Первый вход

1. Открыть `http://kafka-ui.eisnot.ru` → `/auth`.
2. Войти bootstrap-админом из Secret.
3. **User accounts** → создать пользователя `READ` (вместо старого readonly-инстанса).
4. Проверить вход viewer: просмотр работает, запись → 403.

---

## 9. Private registry: imagePullSecret

Если Pod в `ErrImagePull` / `ImagePullBackOff`, а с рабочей станции `docker pull` проходит — обычно нет кредов на нодах или неверный путь/порт образа.

### 9.1. Создать secret

```bash
kubectl -n kafka-ui create secret docker-registry regcred \
  --docker-server=repo.fciit.ru:9445 \
  --docker-username='YOUR_USER' \
  --docker-password='YOUR_PASSWORD' \
  --docker-email='unused@example.com' \
  --dry-run=client -o yaml | kubectl apply -f -
```

`--docker-server` должен совпадать с хостом в image **включая порт**.

### 9.2. Назначить образ и secret

```bash
kubectl -n kafka-ui set image deploy/kafka-ui kafka-ui=repo.fciit.ru:9445/kafka-ui:1.0.0

kubectl -n kafka-ui patch deploy/kafka-ui --type='merge' -p '{
  "spec":{"template":{"spec":{
    "imagePullSecrets":[{"name":"regcred"}],
    "containers":[{"name":"kafka-ui","imagePullPolicy":"Always"}]
  }}}
}'

kubectl -n kafka-ui rollout restart deploy/kafka-ui
kubectl -n kafka-ui rollout status deploy/kafka-ui --timeout=180s
```

### 9.3. Диагностика pull

```bash
kubectl -n kafka-ui get deploy kafka-ui -o jsonpath='image={.spec.template.spec.containers[0].image} secrets={.spec.template.spec.imagePullSecrets}{"\n"}'
kubectl -n kafka-ui describe po -l app.kubernetes.io/name=kafka-ui | sed -n '/Events/,$p'
kubectl -n kafka-ui get secret regcred -o jsonpath='{.data.\.dockerconfigjson}' | base64 -d; echo
```

| Сообщение в Events | Что делать |
|--------------------|------------|
| `unauthorized` / `401` | Пересоздать `regcred`, проверить user/password и `--docker-server` |
| `manifest unknown` / `not found` | Неверный путь/тег (сверьте с `docker pull`) |
| `x509: certificate signed by unknown authority` | Положить CA registry на все ноды (containerd/docker `certs.d`) |
| timeout / connection refused | Сеть/firewall с worker-нод до registry |

> На ноутбуке Docker может быть уже `docker login` и доверять CA. У kubelet на нодах — отдельная конфигурация.

---

## 10. Миграция со схемы admin + readonly

Раньше: два Deployment (`kafka-ui` + `kafka-ui-readonly`).  
Теперь: один Deployment, роли в `users.json`.

```bash
# остановить основной инстанс (PVC сохранить)
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=0

# удалить readonly
kubectl -n kafka-ui delete deploy kafka-ui-readonly --ignore-not-found
kubectl -n kafka-ui delete svc kafka-ui-readonly --ignore-not-found
kubectl -n kafka-ui delete configmap kafka-ui-readonly --ignore-not-found
kubectl -n kafka-ui delete secret kafka-ui-auth-readonly --ignore-not-found
# при необходимости: kubectl -n kafka-ui delete ingress <readonly-ingress>

# образ + креды pull (см. §9) и старт
kubectl -n kafka-ui set image deploy/kafka-ui kafka-ui=repo.fciit.ru:9445/kafka-ui:1.0.0
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=1
kubectl -n kafka-ui rollout status deploy/kafka-ui --timeout=180s
```

Уберите второй host (`kafka-ui-ro.…`) из Ingress, если остался.

Пароли из старых Secret автоматически **не** импортируются — создайте пользователей заново через UI.

---

## 11. Повседневные операции

### Остановка / запуск

```bash
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=0   # стоп, данные на PVC
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=1   # старт
```

### Смена списка Kafka-брокеров

```bash
kubectl apply -f deploy/k8s/manifests/kafka-ui/01-configmap.yaml
kubectl -n kafka-ui rollout restart deploy/kafka-ui
```

### Обновление образа

```bash
kubectl -n kafka-ui set image deploy/kafka-ui kafka-ui=repo.fciit.ru:9445/kafka-ui:1.0.1
kubectl -n kafka-ui rollout status deploy/kafka-ui
# откат:
kubectl -n kafka-ui rollout undo deploy/kafka-ui
```

### Смена пароля пользователя

Через UI **User accounts** → Set password, либо:

```json
PUT /api/auth/users/{username}
{"password":"new-strong-password","role":"READ_WRITE"}
```

Правка Secret `kafka-ui-auth` **не** меняет пароль, если `users.json` уже есть.

### Backup / restore users.json

```bash
POD=$(kubectl -n kafka-ui get pod -l app.kubernetes.io/component=admin -o jsonpath='{.items[0].metadata.name}')
kubectl -n kafka-ui cp "$POD:/etc/kafkaui/users.json" ./users.json.backup

# restore
kubectl -n kafka-ui cp ./users.json.backup "$POD:/etc/kafkaui/users.json"
kubectl -n kafka-ui rollout restart deploy/kafka-ui
```

### Масштабирование

Оставьте **1 replica** (RWO PVC + in-memory сессии).

---

## 12. Health

| Проба | URL |
|-------|-----|
| Liveness / readiness | `/actuator/health` |
| Info | `/actuator/info` |

```bash
kubectl -n kafka-ui port-forward svc/kafka-ui 8080:80
curl -sS http://127.0.0.1:8080/actuator/health
```

---

## 13. Типичные ошибки

| Симптом | Причина | Решение |
|---------|---------|---------|
| `ErrImagePull` / `ImagePullBackOff` | Нет pull-кредов / неверный image / TLS | §9 |
| `CrashLoopBackOff`, ошибка записи `/etc/kafkaui` | Права PVC | `fsGroup: 1000` |
| PVC `Pending` | Нет StorageClass | `kubectl get sc`, задать `storageClassName` |
| 401 / не подходит bootstrap-пароль | Уже есть `users.json` на PVC | Менять пароль в UI / API |
| `READ` не видит User accounts | Ожидаемо | Только `READ_WRITE` |
| Пустой кластер в UI | Неверный bootstrap / сеть | ConfigMap + доступ из pod |
| Сбросились пользователи (Docker) | `docker compose down -v` | Не удалять volume |

---

## 14. Чеклист production

- [ ] Опубликован образ этой ветки (не upstream)
- [ ] В Deployment указан корректный image/тег
- [ ] Создан `imagePullSecret`, подключен к Deployment
- [ ] В ConfigMap реальные bootstrap-серверы
- [ ] Bootstrap-пароль изменён с `ChangeMe-…`
- [ ] PVC `Bound`, есть процедура backup `users.json`
- [ ] DNS → Ingress; TLS желателен
- [ ] Есть запасной `READ_WRITE` и пользователи `READ`
- [ ] Проверен вход обеих ролей
- [ ] Удалён старый `kafka-ui-readonly` и host `kafka-ui-ro.…`

---

## 15. Шпаргалка kubectl

```bash
kubectl -n kafka-ui get po,pvc,svc,ingress,cm,secret
kubectl -n kafka-ui logs -l app.kubernetes.io/component=admin --tail=100
kubectl -n kafka-ui describe po -l app.kubernetes.io/name=kafka-ui
kubectl -n kafka-ui rollout restart deploy/kafka-ui
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=0
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=1
kubectl -n kafka-ui set image deploy/kafka-ui kafka-ui=repo.fciit.ru:9445/kafka-ui:1.0.0
```

---

## 16. Ссылки на файлы в репозитории

| Что | Путь |
|-----|------|
| Полный README (RU) | `README.md` |
| Dockerfile | `deploy/docker/Dockerfile` |
| Compose | `deploy/docker/docker-compose.yaml` |
| K8s манифесты | `deploy/k8s/manifests/kafka-ui/` |
| Confluent Maven cache | `deploy/docker/confluent-m2/` |
| Этот документ для Confluence | `documentation/confluence/Kafka-UI-Operations.md` |
