# Kafka UI в Kubernetes

Подробная инструкция по развёртыванию Kafka UI с локальными учётными записями
и постоянным хранением данных в кластере Kubernetes.

Upstream: https://github.com/provectus/kafka-ui  
Обзор по всей поставке: [deploy/README.md](../../../README.md)

---

## Содержание

1. [Архитектура](#1-архитектура)
2. [Требования](#2-требования)
3. [Состав манифестов](#3-состав-манифестов)
4. [Подготовка образа](#4-подготовка-образа)
5. [Настройка перед apply](#5-настройка-перед-apply)
6. [Первичное развёртывание](#6-первичное-развёртывание)
7. [Первый вход и создание пользователей](#7-первый-вход-и-создание-пользователей)
8. [Постоянные данные](#8-постоянные-данные)
9. [Изменение конфигурации Kafka](#9-изменение-конфигурации-kafka)
10. [Смена bootstrap-пароля и ротация](#10-смена-bootstrap-пароля-и-ротация)
11. [Обновление приложения](#11-обновление-приложения)
12. [Масштабирование и стратегия](#12-масштабирование-и-стратегия)
13. [Ingress, DNS и TLS](#13-ingress-dns-и-tls)
14. [Резервное копирование и восстановление](#14-резервное-копирование-и-восстановление)
15. [Остановка, запуск и удаление](#15-остановка-запуск-и-удаление)
16. [Миграция со схемы admin + readonly](#16-миграция-со-схемы-admin--readonly)
17. [Безопасность](#17-безопасность)
18. [Диагностика](#18-диагностика)
19. [Частые ошибки](#19-частые-ошибки)
20. [Чеклист готовности к prod](#20-чеклист-готовности-к-prod)

---

## 1. Архитектура

В namespace `kafka-ui` разворачивается **один** инстанс приложения.

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
| ConfigMap `kafka-ui` | Постоянная bootstrap-конфигурация: brokers, `AUTH_TYPE`, путь к `users.json` |
| Secret `kafka-ui-auth` | Данные первого администратора (только для создания `users.json`) |
| PVC `kafka-ui-data` | Том с `users.json` (и местом под будущие файлы `/etc/kafkaui`) |
| Deployment `kafka-ui` | Само приложение |
| Service `kafka-ui` | ClusterIP, порт 80 → контейнер 8080 |
| Ingress `kafka-ui` | Внешний HTTP(S)-доступ |

### Роли пользователей

| Роль | Возможности |
|------|-------------|
| `READ` | Просмотр кластеров, топиков, сообщений, схем, Connect и т.д. |
| `READ_WRITE` | Всё из `READ` + изменение Kafka + страница **User accounts** |

Один URL обслуживает оба типа пользователей. Отдельный readonly-инстанс больше не нужен.

---

## 2. Требования

### Кластер

- Kubernetes ≥ 1.21 (проверено на стандартных `apps/v1`, `networking.k8s.io/v1`);
- StorageClass с поддержкой `ReadWriteOnce` (по умолчанию PVC 1Gi);
- Ingress Controller класса `nginx` (или поменяйте `ingressClassName` в `04-ingress.yaml`);
- доступ с pod Kafka UI до Kafka brokers (`KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS`).

### Локальные инструменты

- `kubectl` с контекстом нужного кластера;
- Docker / Buildah / Kaniko — для сборки образа;
- доступ к container registry (или `imagePullPolicy: Never` / `IfNotPresent` на однонодовом кластере).

### Образ

Манифест по умолчанию указывает `kafka-ui-local:latest`. Это **не** публичный
upstream-образ: в нём нет локального multi-user хранилища. Перед apply соберите
образ из корня репозитория (см. [§4](#4-подготовка-образа)).

---

## 3. Состав манифестов

Каталог: `deploy/k8s/manifests/kafka-ui/`

| Файл | Kind | Назначение |
|------|------|------------|
| `00-namespace.yaml` | Namespace | Изоляция ресурсов в `kafka-ui` |
| `01-configmap.yaml` | ConfigMap | Brokers, `AUTH_TYPE`, путь к файлу пользователей |
| `01a-pvc.yaml` | PersistentVolumeClaim | Постоянный том `kafka-ui-data` |
| `01b-secret.yaml` | Secret | Bootstrap username/password первого `READ_WRITE` пользователя |
| `02-deployment.yaml` | Deployment | Pod, envFrom, mount PVC, probes, resources |
| `03-service.yaml` | Service | ClusterIP для Ingress / внутреннего доступа |
| `04-ingress.yaml` | Ingress | Внешний host `kafka-ui.eisnot.ru` |
| `README.md` | — | Этот документ |

Порядок apply по имени файлов совпадает с зависимостями: namespace → config/PVC/secret → deployment → service → ingress.

---

## 4. Подготовка образа

### 4.1. Сборка

Из **корня** репозитория:

```bash
docker build \
  -f deploy/docker/Dockerfile \
  -t registry.example.com/kafka-ui:local-1 \
  .
```

Dockerfile многостадийный:

1. frontend (`node:16.15.0`) — сборка React UI;
2. backend (`maven` + JDK 17) — упаковка Spring Boot jar;
3. runtime (`azul/zulu-openjdk-alpine:17`) — пользователь `kafkaui`, порт 8080.

### 4.2. Публикация в registry

```bash
docker push registry.example.com/kafka-ui:local-1
```

Для приватного registry добавьте `imagePullSecrets` в `02-deployment.yaml`
или используйте уже настроенный service account ноды / namespace.

### 4.3. Указать образ в Deployment

В `02-deployment.yaml`:

```yaml
image: registry.example.com/kafka-ui:local-1
imagePullPolicy: IfNotPresent
```

Рекомендация: используйте неизменяемый тег (`local-1`, git SHA), а не только `latest`.

### 4.4. Однонодовый / kind / minikube без registry

```bash
# kind
kind load docker-image kafka-ui-local:latest --name <cluster>

# minikube
minikube image load kafka-ui-local:latest
```

В Deployment:

```yaml
image: kafka-ui-local:latest
imagePullPolicy: Never   # или IfNotPresent
```

---

## 5. Настройка перед apply

### 5.1. Kafka brokers — `01-configmap.yaml`

```yaml
data:
  KAFKA_CLUSTERS_0_NAME: prod
  KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: 10.251.86.237:9092,10.251.86.238:9092,10.251.86.239:9092
  AUTH_TYPE: LOGIN_FORM
  AUTH_LOCAL_USERS_FILE: /etc/kafkaui/users.json
```

Замените bootstrap-адреса на актуальные. Pod должен иметь сетевой доступ к этим портам.

Дополнительный кластер:

```yaml
  KAFKA_CLUSTERS_1_NAME: staging
  KAFKA_CLUSTERS_1_BOOTSTRAPSERVERS: staging-kafka:9092
```

Опционально Schema Registry / Connect (стандартные свойства Provectus Kafka UI):

```yaml
  KAFKA_CLUSTERS_0_SCHEMAREGISTRY: http://schema-registry:8081
  KAFKA_CLUSTERS_0_KAFKACONNECT_0_NAME: connect
  KAFKA_CLUSTERS_0_KAFKACONNECT_0_ADDRESS: http://kafka-connect:8083
```

### 5.2. Первый администратор — `01b-secret.yaml`

```yaml
stringData:
  AUTH_LOCAL_BOOTSTRAP_USERNAME: admin
  AUTH_LOCAL_BOOTSTRAP_PASSWORD: "ChangeMe-kafka-ui"
```

**Обязательно смените пароль до первого apply в prod.**

Эти значения используются **только** если на PVC ещё нет `users.json`.
После создания файла дальнейшие пользователи и пароли живут на томе; изменение
Secret само по себе пароль в UI не меняет.

### 5.3. Размер и класс тома — `01a-pvc.yaml`

```yaml
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
  # storageClassName: your-storage-class   # раскомментируйте при необходимости
```

1Gi достаточно для `users.json`. Укажите `storageClassName`, если default StorageClass
не подходит (например, нужен Retain / snapshot-класс).

### 5.4. Ingress host — `04-ingress.yaml`

```yaml
rules:
  - host: kafka-ui.eisnot.ru
```

Замените на ваш DNS-имя. При необходимости включите TLS (см. [§13](#13-ingress-dns-и-tls)).

### 5.5. Ресурсы — `02-deployment.yaml`

По умолчанию:

```yaml
resources:
  requests:
    cpu: 100m
    memory: 256Mi
  limits:
    cpu: "1"
    memory: 1Gi
```

Для крупных кластеров увеличьте `memory` limit (512Mi–2Gi).

---

## 6. Первичное развёртывание

Выполняйте из каталога `deploy/k8s` или указывайте полный путь.

### 6.1. Проверка контекста

```bash
kubectl config current-context
kubectl get nodes
kubectl get sc
```

Убедитесь, что есть StorageClass и Ingress Controller:

```bash
kubectl get pods -n ingress-nginx   # или ваш namespace controller
```

### 6.2. Apply всех манифестов

```bash
kubectl apply -f manifests/kafka-ui/
```

Эквивалентно пошагово:

```bash
kubectl apply -f manifests/kafka-ui/00-namespace.yaml
kubectl apply -f manifests/kafka-ui/01-configmap.yaml
kubectl apply -f manifests/kafka-ui/01a-pvc.yaml
kubectl apply -f manifests/kafka-ui/01b-secret.yaml
kubectl apply -f manifests/kafka-ui/02-deployment.yaml
kubectl apply -f manifests/kafka-ui/03-service.yaml
kubectl apply -f manifests/kafka-ui/04-ingress.yaml
```

### 6.3. Дождаться готовности

```bash
kubectl -n kafka-ui rollout status deploy/kafka-ui --timeout=180s
kubectl -n kafka-ui get po,pvc,svc,ingress
```

Ожидаемое состояние:

| Объект | Статус |
|--------|--------|
| Pod `kafka-ui-…` | `1/1 Running` |
| PVC `kafka-ui-data` | `Bound` |
| Service `kafka-ui` | ClusterIP |
| Ingress `kafka-ui` | ADDRESS заполнен (после назначения LB/Node) |

### 6.4. Проверка health

```bash
kubectl -n kafka-ui port-forward svc/kafka-ui 8080:80
curl -sS http://127.0.0.1:8080/actuator/health
```

Ожидается JSON со статусом `UP` (или аналог Spring Actuator).

### 6.5. Проверка логов первого старта

```bash
kubectl -n kafka-ui logs -l app.kubernetes.io/component=admin --tail=100
```

Ищите строку о конфигурации `LOGIN_FORM`. При первом старте создаётся
`/etc/kafkaui/users.json` с bootstrap-администратором.

---

## 7. Первый вход и создание пользователей

### 7.1. Вход

1. Откройте `http://kafka-ui.eisnot.ru` (или ваш host / port-forward).
2. Форма логина: `/auth`.
3. Логин/пароль — значения из Secret `kafka-ui-auth`
   (по умолчанию в манифесте: `admin` / `ChangeMe-kafka-ui`).

### 7.2. Создание пользователя только для чтения

1. Войдите как `READ_WRITE`.
2. В боковом меню откройте **User accounts**.
3. Создайте пользователя, например:
   - Username: `viewer`
   - Password: не короче 8 символов
   - Permission: **Read**
4. Выйдите (`Log out`) и войдите как `viewer` — UI доступен на просмотр,
   операции изменения Kafka API вернут отказ (403).

### 7.3. Создание второго администратора

Аналогично, Permission: **Read / write**. Иметь второго `READ_WRITE` полезно
до ротации / удаления первого.

### 7.4. API (опционально)

После логина через браузер можно использовать cookie-сессию:

```bash
# пример: получить текущего пользователя
curl -sS -b cookies.txt 'https://kafka-ui.eisnot.ru/api/auth/me'
```

Создание пользователя:

```bash
curl -sS -b cookies.txt -X POST 'https://kafka-ui.eisnot.ru/api/auth/users' \
  -H 'Content-Type: application/json' \
  -d '{"username":"viewer","password":"viewer-password","role":"READ"}'
```

---

## 8. Постоянные данные

### 8.1. Что где лежит

| Данные | Место | Переживает recreate pod? | Переживает delete PVC? |
|--------|-------|--------------------------|-------------------------|
| `users.json` | PVC `kafka-ui-data` → `/etc/kafkaui/users.json` | Да | Нет |
| Brokers / auth type | ConfigMap `kafka-ui` | Да | Да (это не PVC) |
| Bootstrap credentials | Secret `kafka-ui-auth` | Да | Да |
| In-memory кэш метрик | память pod | Нет (пересобирается) | — |

### 8.2. Почему volume на весь `/etc/kafkaui`

Каталог — стандартное место данных Kafka UI. Сейчас туда пишется `users.json`.
После возможного merge Configuration Wizard из `origin/master` в тот же том
можно класть `dynamic_config.yaml` и `uploads/` без смены mount path.

### 8.3. Права на том

В Deployment задано:

```yaml
securityContext:
  fsGroup: 1000
  fsGroupChangePolicy: OnRootMismatch
```

Контейнер работает от пользователя `kafkaui`. `fsGroup: 1000` нужен, чтобы
запись в PVC была возможна на типичных StorageClass.

### 8.4. Что нельзя делать

- `kubectl delete -f manifests/kafka-ui/` — удалит и PVC, и пользователей;
- менять `AUTH_LOCAL_USERS_FILE` на путь вне PVC без переноса файла;
- масштабировать Deployment выше 1 replica с `ReadWriteOnce` без общего RWX-тома
  (см. [§12](#12-масштабирование-и-стратегия)).

---

## 9. Изменение конфигурации Kafka

Список серверов **не** редактируется через UI в этой ветке — только через ConfigMap.

```bash
# отредактируйте 01-configmap.yaml, затем:
kubectl apply -f manifests/kafka-ui/01-configmap.yaml
kubectl -n kafka-ui rollout restart deploy/kafka-ui
kubectl -n kafka-ui rollout status deploy/kafka-ui
```

Либо точечно:

```bash
kubectl -n kafka-ui edit configmap kafka-ui
kubectl -n kafka-ui rollout restart deploy/kafka-ui
```

`users.json` при этом **не** затрагивается: PVC остаётся тем же.

---

## 10. Смена bootstrap-пароля и ротация

### 10.1. Смена пароля уже существующего пользователя

Через UI **User accounts** → поле New password → **Set password**.  
Или API `PUT /api/auth/users/{username}` с телом:

```json
{"password":"new-strong-password","role":"READ_WRITE"}
```

### 10.2. Обновление Secret (влияет только на «чистый» PVC)

```bash
kubectl -n kafka-ui create secret generic kafka-ui-auth \
  --from-literal=AUTH_LOCAL_BOOTSTRAP_USERNAME=admin \
  --from-literal=AUTH_LOCAL_BOOTSTRAP_PASSWORD='НОВЫЙ_ПАРОЛЬ' \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n kafka-ui rollout restart deploy/kafka-ui
```

Если `users.json` уже есть, bootstrap-пароль из Secret **не** применяется.
Чтобы «сбросить» пользователей на bootstrap:

1. Сделайте backup PVC.
2. Остановите Deployment (`replicas: 0`).
3. Удалите `users.json` с тома (через временный pod с тем же PVC) **или**
   удалите PVC и создайте заново (потеряете всех пользователей).
4. Запустите Deployment снова.

---

## 11. Обновление приложения

```bash
# 1. Собрать новый образ
docker build -f deploy/docker/Dockerfile -t registry.example.com/kafka-ui:local-2 .
docker push registry.example.com/kafka-ui:local-2

# 2. Обновить image в 02-deployment.yaml и применить
kubectl apply -f manifests/kafka-ui/02-deployment.yaml

# либо без правки файла:
kubectl -n kafka-ui set image deploy/kafka-ui kafka-ui=registry.example.com/kafka-ui:local-2
kubectl -n kafka-ui rollout status deploy/kafka-ui
```

Стратегия `Recreate` сначала останавливает старый pod, затем поднимает новый —
это нужно для `ReadWriteOnce` PVC (два pod одновременно том не разделят).

ConfigMap / Secret / PVC при обычном обновлении образа не трогайте.

Откат:

```bash
kubectl -n kafka-ui rollout undo deploy/kafka-ui
kubectl -n kafka-ui rollout status deploy/kafka-ui
```

---

## 12. Масштабирование и стратегия

Текущая конфигурация рассчитана на **1 replica**.

```yaml
spec:
  replicas: 1
  strategy:
    type: Recreate
```

Причины:

- PVC с `ReadWriteOnce` обычно монтируется только на одну ноду / один pod;
- локальные сессии form-login хранятся в памяти JVM (sticky session на Ingress
  помогает при нескольких репликах, но файл пользователей всё равно нуждается
  в общем хранилище).

Если нужны несколько реплик:

1. Используйте StorageClass с `ReadWriteMany` (NFS, CephFS и т.п.) и
   `accessModes: [ReadWriteMany]` в PVC;
2. Включите sticky sessions на Ingress (уже есть cookie affinity);
3. Учтите гонки записи в `users.json` (файл-хранилище синхронизировано
   внутри одного процесса; несколько процессов требуют внешней БД — сейчас её нет).

Практическая рекомендация для prod на этом форке: **оставьте 1 replica**.

---

## 13. Ingress, DNS и TLS

### 13.1. DNS

Создайте A/CNAME запись:

```
kafka-ui.eisnot.ru  →  IP Ingress Controller / LoadBalancer
```

Проверка:

```bash
kubectl -n kafka-ui get ingress kafka-ui -o wide
dig +short kafka-ui.eisnot.ru
```

### 13.2. HTTP без TLS

Работает «из коробки» с `04-ingress.yaml`, если Ingress Controller класса `nginx`.

Другой controller — измените:

```yaml
spec:
  ingressClassName: your-class
```

### 13.3. TLS

1. Создайте TLS Secret (cert-manager или вручную):

```bash
kubectl -n kafka-ui create secret tls kafka-ui-tls \
  --cert=fullchain.pem \
  --key=privkey.pem
```

2. В `04-ingress.yaml` раскомментируйте:

```yaml
  tls:
    - hosts:
        - kafka-ui.eisnot.ru
      secretName: kafka-ui-tls
```

3. Apply:

```bash
kubectl apply -f manifests/kafka-ui/04-ingress.yaml
```

### 13.4. Аннотации nginx

Уже заданы:

| Аннотация | Зачем |
|-----------|-------|
| `proxy-body-size: 50m` | Крупные payload (сообщения, конфиги) |
| `proxy-read/send-timeout: 3600` | Длинные SSE/стримы сообщений |
| `affinity: cookie` | Sticky session (на будущее / обновления) |

---

## 14. Резервное копирование и восстановление

### 14.1. Backup пользователей

```bash
POD=$(kubectl -n kafka-ui get pod -l app.kubernetes.io/component=admin -o jsonpath='{.items[0].metadata.name}')
kubectl -n kafka-ui cp "$POD:/etc/kafkaui/users.json" ./users.json.backup
```

Также сохраните манифесты ConfigMap/Secret в Git (без реальных prod-паролей —
через sealed-secrets / external-secrets).

### 14.2. Snapshot PVC

Зависит от StorageClass / CSI. Пример идеи (если VolumeSnapshot CRD доступны):

```bash
kubectl -n kafka-ui apply -f - <<'EOF'
apiVersion: snapshot.storage.k8s.io/v1
kind: VolumeSnapshot
metadata:
  name: kafka-ui-data-$(date +%Y%m%d)
  namespace: kafka-ui
spec:
  source:
    persistentVolumeClaimName: kafka-ui-data
EOF
```

Уточните `VolumeSnapshotClass` у администраторов кластера.

### 14.3. Восстановление `users.json`

```bash
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=0
# поднимите временный pod с PVC kafka-ui-data и скопируйте файл,
# либо используйте kubectl cp в уже запущенный pod:
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=1
POD=$(kubectl -n kafka-ui get pod -l app.kubernetes.io/component=admin -o jsonpath='{.items[0].metadata.name}')
kubectl -n kafka-ui cp ./users.json.backup "$POD:/etc/kafkaui/users.json"
kubectl -n kafka-ui rollout restart deploy/kafka-ui
```

---

## 15. Остановка, запуск и удаление

### Остановить (данные сохранить)

```bash
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=0
```

PVC и ConfigMap/Secret остаются.

### Запустить

```bash
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=1
kubectl -n kafka-ui rollout status deploy/kafka-ui
```

### Удалить приложение, сохранив пользователей

```bash
kubectl -n kafka-ui delete deploy,svc,ingress kafka-ui
# PVC kafka-ui-data и Secret/ConfigMap оставьте
```

### Полное удаление (включая данные)

```bash
kubectl delete -f manifests/kafka-ui/
# или:
kubectl delete ns kafka-ui
```

> Полное удаление уничтожит PVC и всех пользователей. Перед этим сделайте backup.

---

## 16. Миграция со схемы admin + readonly

Старая схема (два инстанса) в этой ветке удалена из манифестов.

Порядок миграции:

1. Соберите образ этой ветки и задеплойте **новый** Deployment с PVC
   (можно во временном namespace для проверки).
2. Войдите как bootstrap-admin, создайте:
   - пользователя `READ_WRITE` (рабочий admin);
   - пользователя `READ` вместо старого readonly-инстанса.
3. Переключите Ingress host на новый Service.
4. Удалите старые объекты, если они ещё есть:

```bash
kubectl -n kafka-ui delete deploy kafka-ui-readonly --ignore-not-found
kubectl -n kafka-ui delete svc kafka-ui-readonly --ignore-not-found
kubectl -n kafka-ui delete configmap kafka-ui-readonly --ignore-not-found
kubectl -n kafka-ui delete secret kafka-ui-auth-readonly --ignore-not-found
```

5. Уберите второй host из Ingress (`kafka-ui-ro.…`), если он остался.

Пароли старых Secret автоматически не импортируются.

---

## 17. Безопасность

| Практика | Рекомендация |
|----------|--------------|
| Bootstrap-пароль | Сменить до первого prod-apply; не коммитить реальные пароли |
| Secret в Git | Использовать Sealed Secrets / SOPS / External Secrets |
| TLS | Включить на Ingress |
| Сеть | NetworkPolicy: разрешить egress только к Kafka / SR / Connect |
| RBAC Kubernetes | Отдельный ServiceAccount с минимальными правами (сейчас SA по умолчанию) |
| PVC | Запретить случайный `kubectl delete pvc`; backup по расписанию |
| Роли приложения | Минимум один запасной `READ_WRITE`; viewer — только `READ` |

Файл `users.json` содержит только хеши паролей, но всё равно считается чувствительным:
ограничьте доступ к PVC и backup-копиям.

---

## 18. Диагностика

### Статус объектов

```bash
kubectl -n kafka-ui get all,pvc,ingress,cm,secret
kubectl -n kafka-ui describe deploy kafka-ui
kubectl -n kafka-ui describe pvc kafka-ui-data
```

### Логи

```bash
kubectl -n kafka-ui logs -l app.kubernetes.io/component=admin --tail=200 -f
```

### События

```bash
kubectl -n kafka-ui get events --sort-by=.lastTimestamp | tail -50
```

### Проверка содержимого тома

```bash
POD=$(kubectl -n kafka-ui get pod -l app.kubernetes.io/component=admin -o jsonpath='{.items[0].metadata.name}')
kubectl -n kafka-ui exec "$POD" -- ls -la /etc/kafkaui
kubectl -n kafka-ui exec "$POD" -- sh -c 'test -f /etc/kafkaui/users.json && echo OK'
```

Не выводите `users.json` в общие чаты — там хеши паролей и имена учёток.

### Сетевая проверка до Kafka

```bash
POD=$(kubectl -n kafka-ui get pod -l app.kubernetes.io/component=admin -o jsonpath='{.items[0].metadata.name}')
kubectl -n kafka-ui exec "$POD" -- sh -c 'nc -zv 10.251.86.237 9092 || true'
```

(в alpine-образе `nc` может отсутствовать — используйте временный debug-pod в той же сети).

---

## 19. Частые ошибки

| Симптом | Причина | Решение |
|---------|---------|---------|
| `ImagePullBackOff` | Образ не в registry / неверный тег | Собрать, push, поправить `image` |
| `CrashLoopBackOff`, ошибка записи в `/etc/kafkaui` | Права PVC / нет fsGroup | Проверить `fsGroup: 1000`, StorageClass |
| PVC `Pending` | Нет StorageClass / квоты | `kubectl get sc`, указать `storageClassName` |
| 401 / вечный логин | Неверный пароль или другой `users.json` | Проверить Secret только для чистого PVC; иначе пароль из UI |
| Пользователь `READ` не видит **User accounts** | Ожидаемо | Страница только для `READ_WRITE` |
| `READ` не может создать топик | Ожидаемо | Нужна роль `READ_WRITE` |
| После apply Secret пароль не сменился | `users.json` уже существует | Менять пароль через UI / API |
| Два pod в `ContainerCreating` + том | replicas > 1 на RWO | Вернуть `replicas: 1` |
| Ingress без ADDRESS | Нет Ingress Controller | Установить nginx ingress / поменять class |
| Кластер пустой в UI | Неверный bootstrap / сеть | Проверить ConfigMap и connectivity |

---

## 20. Чеклист готовности к prod

- [ ] Собран и опубликован свой образ (не upstream `provectuslabs/kafka-ui`)
- [ ] В `02-deployment.yaml` указан правильный `image` / тег
- [ ] В ConfigMap заданы реальные bootstrap-серверы Kafka
- [ ] Bootstrap-пароль в Secret изменён с `ChangeMe-…`
- [ ] PVC в статусе `Bound`, backup-процедура понятна
- [ ] DNS указывает на Ingress
- [ ] TLS включён (желательно)
- [ ] Создан запасной пользователь `READ_WRITE`
- [ ] Созданы пользователи `READ` для зрителей
- [ ] Проверен вход обоих ролей
- [ ] Документирован порядок обновления образа
- [ ] Старый readonly Deployment удалён (если был)

---

## Краткие команды (шпаргалка)

```bash
# деплой
kubectl apply -f manifests/kafka-ui/
kubectl -n kafka-ui rollout status deploy/kafka-ui

# статус
kubectl -n kafka-ui get po,pvc,svc,ingress

# логи
kubectl -n kafka-ui logs -l app.kubernetes.io/component=admin --tail=100

# рестарт после правки ConfigMap
kubectl apply -f manifests/kafka-ui/01-configmap.yaml
kubectl -n kafka-ui rollout restart deploy/kafka-ui

# стоп / старт
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=0
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=1

# backup пользователей
POD=$(kubectl -n kafka-ui get pod -l app.kubernetes.io/component=admin -o jsonpath='{.items[0].metadata.name}')
kubectl -n kafka-ui cp "$POD:/etc/kafkaui/users.json" ./users.json.backup
```
