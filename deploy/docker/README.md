# Docker deployment

Set the initial cluster and administrator password, then start the custom image:

```bash
export KAFKA_UI_BOOTSTRAP_SERVERS=kafka:9092
export KAFKA_UI_ADMIN_PASSWORD='replace-with-a-strong-password'
docker compose up --build -d
```

The cluster settings are part of `docker-compose.yaml`/its environment and
survive container replacement. Local accounts are stored in the named volume
`kafka-ui-data` as BCrypt hashes. `docker compose down` keeps the volume;
`docker compose down -v` deletes it.

This `dev` source tree does not include the upstream Configuration Wizard.
Runtime cluster editing requires synchronizing the source with
`origin/master`; the existing `/etc/kafkaui` volume is suitable for its
`dynamic_config.yaml` and uploaded files after that migration.
