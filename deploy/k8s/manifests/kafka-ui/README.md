# Kafka UI (Provectus)

Upstream: https://github.com/provectus/kafka-ui

Манифест использует `kafka-ui-local:latest`, потому что поддержка локальных
пользователей входит в эту ветку, а не в upstream-образ. Перед apply соберите
`deploy/docker/Dockerfile`, отправьте образ в ваш registry и замените
`image` в `02-deployment.yaml`.

URL: http://kafka-ui.eisnot.ru

В этой сборке `LOGIN_FORM` использует несколько локальных учётных записей. Роли:
- `READ` — просмотр данных;
- `READ_WRITE` — просмотр, изменение Kafka и управление учётными записями.

Первый пользователь создаётся из `01b-secret.yaml`. После входа новые учётные
записи создаются на странице **User accounts**.

## Состав

| Файл | Назначение |
|------|------------|
| `00-namespace.yaml` | namespace `kafka-ui` |
| `01-configmap.yaml` | постоянная bootstrap-конфигурация brokers и auth |
| `01a-pvc.yaml` | постоянный том для локальных пользователей |
| `01b-secret.yaml` | данные первого администратора |
| `02-deployment.yaml` | Deployment и подключение PVC |
| `03-service.yaml` | Service |
| `04-ingress.yaml` | `kafka-ui.eisnot.ru` |

---

## Поднять

```bash
kubectl apply -f manifests/kafka-ui/
kubectl -n kafka-ui rollout status deploy/kafka-ui
kubectl -n kafka-ui get po,svc,ingress
```

DNS:
- `kafka-ui.eisnot.ru` → Ingress

---

## Остановить / запустить

```bash
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=0
kubectl -n kafka-ui scale deploy/kafka-ui --replicas=1
```

---

## Удалить

```bash
kubectl delete -f manifests/kafka-ui/
```

> Эта команда удаляет и PVC. Для обычного обновления используйте `kubectl apply`
> и `kubectl rollout restart`. Не удаляйте `kafka-ui-data`, если данные нужны.

---

## Учётные записи

### Первый запуск
```bash
kubectl -n kafka-ui create secret generic kafka-ui-auth \
  --from-literal=AUTH_LOCAL_BOOTSTRAP_USERNAME=admin \
  --from-literal=AUTH_LOCAL_BOOTSTRAP_PASSWORD='ВАШ_ПАРОЛЬ' \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n kafka-ui rollout restart deploy/kafka-ui
```

Bootstrap-данные используются только когда `/etc/kafkaui/users.json` отсутствует.
Последующие изменения выполняются через UI и сохраняются на PVC.

## Постоянные данные

PVC `kafka-ui-data` содержит:
- `users.json` — учётные записи (пароли хранятся как BCrypt-хеши).

Список Kafka-серверов хранится в Kubernetes ConfigMap `kafka-ui`, поэтому
переживает пересоздание pod и rollout. Ветка `dev` не содержит Configuration
Wizard из upstream `v0.7.2`: переменная `DYNAMIC_CONFIG_ENABLED` для собранного
из этой ветки образа не поддерживается. Для runtime wizard сначала необходимо
синхронизировать исходники с `origin/master`; после этого тот же PVC может
хранить `/etc/kafkaui/dynamic_config.yaml` и `/etc/kafkaui/uploads/`.

Перед удалением PVC сделайте snapshot или резервную копию средствами вашего
storage class.

---

## Конфиг Kafka

Начальный broker в `01-configmap.yaml`:
```yaml
KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: 10.251.86.237:9092,10.251.86.238:9092,10.251.86.239:9092
```

После правки:
```bash
kubectl apply -f manifests/kafka-ui/01-configmap.yaml
kubectl -n kafka-ui rollout restart deploy/kafka-ui
```

---

## Troubleshooting

```bash
kubectl -n kafka-ui get po -l app.kubernetes.io/name=kafka-ui -o wide
kubectl -n kafka-ui logs -l app.kubernetes.io/component=admin --tail=50
```
