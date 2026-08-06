# Kafka UI (Provectus)

Upstream: https://github.com/provectus/kafka-ui  
Image: `provectuslabs/kafka-ui:v0.7.2`

| Роль | URL | Логин (default) | Пароль (default) | Режим |
|------|-----|-----------------|------------------|--------|
| admin | http://kafka-ui.eisnot.ru | `admin` | `ChangeMe-kafka-ui` | полный доступ |
| readonly | http://kafka-ui-ro.eisnot.ru | `readonly` | `ChangeMe-readonly` | только просмотр (`READONLY=true`) |

> В Provectus Kafka UI `LOGIN_FORM` = **один пользователь на инстанс**.  
> Поэтому readonly — отдельный Deployment с `KAFKA_CLUSTERS_0_READONLY=true`.

## Состав

| Файл | Назначение |
|------|------------|
| `00-namespace.yaml` | namespace `kafka-ui` |
| `01-configmap.yaml` | admin: brokers + AUTH |
| `01b-secret.yaml` | admin credentials |
| `01c-configmap-readonly.yaml` | readonly: brokers + `READONLY=true` |
| `01d-secret-readonly.yaml` | readonly credentials |
| `02-deployment.yaml` | admin Deployment |
| `02b-deployment-readonly.yaml` | readonly Deployment |
| `03-service.yaml` / `03b-…` | Services |
| `04-ingress.yaml` | `kafka-ui.eisnot.ru` + `kafka-ui-ro.eisnot.ru` |

---

## Поднять

```bash
kubectl apply -f manifests/kafka-ui/
kubectl -n kafka-ui rollout status deploy/kafka-ui
kubectl -n kafka-ui rollout status deploy/kafka-ui-readonly
kubectl -n kafka-ui get po,svc,ingress
```

DNS:
- `kafka-ui.eisnot.ru` → Ingress
- `kafka-ui-ro.eisnot.ru` → Ingress

---

## Остановить / запустить

Оба инстанса:
```bash
kubectl -n kafka-ui scale deploy/kafka-ui deploy/kafka-ui-readonly --replicas=0
kubectl -n kafka-ui scale deploy/kafka-ui deploy/kafka-ui-readonly --replicas=1
```

Только readonly:
```bash
kubectl -n kafka-ui scale deploy/kafka-ui-readonly --replicas=0
kubectl -n kafka-ui scale deploy/kafka-ui-readonly --replicas=1
```

---

## Удалить

```bash
kubectl delete -f manifests/kafka-ui/
# или
kubectl delete ns kafka-ui
```

---

## Учётные записи

### Admin
- Secret: `kafka-ui-auth`
- Полный доступ к кластеру `prod`

### Readonly
- Secret: `kafka-ui-auth-readonly`
- UI в read-only: нельзя создавать/удалять топики, менять конфиги и т.п.

### Сменить пароль readonly
```bash
kubectl -n kafka-ui create secret generic kafka-ui-auth-readonly \
  --from-literal=SPRING_SECURITY_USER_NAME=readonly \
  --from-literal=SPRING_SECURITY_USER_PASSWORD='ВАШ_ПАРОЛЬ' \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n kafka-ui rollout restart deploy/kafka-ui-readonly
```

### Сменить пароль admin
```bash
kubectl -n kafka-ui create secret generic kafka-ui-auth \
  --from-literal=SPRING_SECURITY_USER_NAME=admin \
  --from-literal=SPRING_SECURITY_USER_PASSWORD='ВАШ_ПАРОЛЬ' \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n kafka-ui rollout restart deploy/kafka-ui
```

---

## Конфиг Kafka

Одинаковые brokers в `01-configmap.yaml` и `01c-configmap-readonly.yaml`:
```yaml
KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: 10.251.86.237:9092,10.251.86.238:9092,10.251.86.239:9092
```

После правки:
```bash
kubectl apply -f manifests/kafka-ui/01-configmap.yaml
kubectl apply -f manifests/kafka-ui/01c-configmap-readonly.yaml
kubectl -n kafka-ui rollout restart deploy/kafka-ui deploy/kafka-ui-readonly
```

---

## Troubleshooting

```bash
kubectl -n kafka-ui get po -l app.kubernetes.io/name=kafka-ui -o wide
kubectl -n kafka-ui logs -l app.kubernetes.io/component=admin --tail=50
kubectl -n kafka-ui logs -l app.kubernetes.io/component=readonly --tail=50
```
