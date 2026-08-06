# Kafka-UI Helm Chart

## Configuration

Most of the Helm charts parameters are common, follow table describe unique parameters related to application configuration.

### Kafka-UI parameters

| Parameter                                | Description                                                                                                                                    | Default |
| ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ------- |
| `existingConfigMap`                      | Name of the existing ConfigMap with Kafka-UI environment variables                                                                             | `nil`   |
| `existingSecret`                         | Name of the existing Secret with Kafka-UI environment variables                                                                                | `nil`   |
| `envs.secret`                            | Set of the sensitive environment variables to pass to Kafka-UI                                                                                 | `{}`    |
| `envs.config`                            | Set of the environment variables to pass to Kafka-UI                                                                                           | `{}`    |
| `yamlApplicationConfigConfigMap`         | Map with name and keyName keys, name refers to the existing ConfigMap, keyName refers to the ConfigMap key with Kafka-UI config in Yaml format | `{}`    |
| `yamlApplicationConfig`                  | Kafka-UI config in Yaml format                                                                                                                 | `{}`    |
| `networkPolicy.enabled`                  | Enable network policies                                                                                                                        | `false` |
| `networkPolicy.egressRules.customRules`  | Custom network egress policy rules                                                                                                             | `[]`    |
| `networkPolicy.ingressRules.customRules` | Custom network ingress policy rules                                                                                                            | `[]`    |
| `podLabels`                              | Extra labels for Kafka-UI pod                                                                                                                  | `{}`    |


## Example

To install Kafka-UI need to execute follow:
``` bash
helm repo add kafka-ui https://provectus.github.io/kafka-ui
helm install kafka-ui kafka-ui/kafka-ui --set envs.config.KAFKA_CLUSTERS_0_NAME=local --set envs.config.KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=kafka:9092
```
To connect to Kafka-UI web application need to execute:
``` bash
kubectl port-forward svc/kafka-ui 8080:80
```
Open the `http://127.0.0.1:8080` on the browser to access Kafka-UI.

## Локальные пользователи и persistence (этот форк)

Полная документация: [`deploy/README.md`](../../deploy/README.md), Kubernetes: [`deploy/k8s/manifests/kafka-ui/README.md`](../../deploy/k8s/manifests/kafka-ui/README.md).

| Values | Описание |
|--------|----------|
| `persistence.enabled` | Создать PVC и смонтировать в `/etc/kafkaui` |
| `persistence.size` | Размер тома (по умолчанию `1Gi`) |
| `persistence.storageClass` | StorageClass (пусто = default) |
| `persistence.existingClaim` | Использовать уже существующий PVC |
| `persistence.mountPath` | Путь монтирования (по умолчанию `/etc/kafkaui`) |

Пример установки с локальными пользователями:

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

Нужен образ, собранный из этой ветки (`deploy/docker/Dockerfile`), а не upstream `provectuslabs/kafka-ui`.
