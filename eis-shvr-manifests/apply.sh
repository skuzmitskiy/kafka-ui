#!/usr/bin/env bash
set -euo pipefail

# Apply as-search-in-shvr manifests to namespace eis-shvr.
# Requires: kubectl configured for the target cluster, and secret.yml present
# (copy from secret.example.yml and fill credentials; secret.yml is gitignored).

ROOT="$(cd "$(dirname "$0")" && pwd)"
NS=eis-shvr

if [[ ! -f "$ROOT/secret.yml" ]]; then
  echo "Missing $ROOT/secret.yml"
  echo "Copy secret.example.yml -> secret.yml and fill DB / TFS / FS credentials."
  exit 1
fi

kubectl get ns "$NS" >/dev/null
kubectl apply -f "$ROOT/configMap-1.yml"
kubectl apply -f "$ROOT/secret.yml"
kubectl apply -f "$ROOT/app-deploy.yaml"
kubectl -n "$NS" get cm,secret,deploy,svc,servicemonitor -l app=as-search-in-shvr 2>/dev/null || \
  kubectl -n "$NS" get cm as-search-in-shvr-config secret as-search-in-shvr-secrets deploy as-search-in-shvr svc as-search-in-shvr
