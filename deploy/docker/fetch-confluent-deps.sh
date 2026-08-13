#!/usr/bin/env bash
# Refresh vendored Confluent Maven artifacts for offline/RF Docker builds.
# Requires network access to https://packages.confluent.io/maven/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${ROOT}/deploy/docker/confluent-m2"
VERSION="${CONFLUENT_VERSION:-7.3.0}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

ARTIFACTS=(
  "io.confluent:kafka-schema-registry-client:${VERSION}"
  "io.confluent:kafka-avro-serializer:${VERSION}"
  "io.confluent:kafka-json-schema-serializer:${VERSION}"
  "io.confluent:kafka-protobuf-serializer:${VERSION}"
)

cat >"$TMP/settings.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<settings>
  <mirrors>
    <mirror>
      <id>central-mirror</id>
      <url>https://repo1.maven.org/maven2</url>
      <mirrorOf>*,!confluent</mirrorOf>
    </mirror>
  </mirrors>
  <profiles>
    <profile>
      <id>p</id>
      <repositories>
        <repository>
          <id>central</id>
          <url>https://repo1.maven.org/maven2</url>
        </repository>
        <repository>
          <id>confluent</id>
          <url>https://packages.confluent.io/maven/</url>
        </repository>
      </repositories>
    </profile>
  </profiles>
  <activeProfiles><activeProfile>p</activeProfile></activeProfiles>
</settings>
EOF

echo "Downloading Confluent ${VERSION} deps into ${OUT} ..."
rm -rf "$TMP/repo"
mkdir -p "$TMP/repo"

for art in "${ARTIFACTS[@]}"; do
  echo "  get ${art}"
  mvn -B -s "$TMP/settings.xml" \
    org.apache.maven.plugins:maven-dependency-plugin:3.6.0:get \
    -Dartifact="${art}" \
    -DremoteRepositories=confluent::::https://packages.confluent.io/maven/ \
    -Dmaven.repo.local="$TMP/repo" \
    -f "${ROOT}/pom.xml" \
    -N
done

rm -rf "$OUT"
mkdir -p "$OUT"
# Keep only Confluent-hosted coordinates (plus kafka-clients CCS if present).
if [[ -d "$TMP/repo/io/confluent" ]]; then
  mkdir -p "$OUT/io"
  cp -a "$TMP/repo/io/confluent" "$OUT/io/"
fi
if [[ -d "$TMP/repo/org/apache/kafka/kafka-clients" ]]; then
  mkdir -p "$OUT/org/apache/kafka"
  cp -a "$TMP/repo/org/apache/kafka/kafka-clients" "$OUT/org/apache/kafka/"
fi

find "$OUT" -name '*.lastUpdated' -delete
find "$OUT" -name '_remote.repositories' -delete

echo "Done. Size: $(du -sh "$OUT" | awk '{print $1}')"
find "$OUT" -type f | wc -l | xargs -I{} echo "Files: {}"
