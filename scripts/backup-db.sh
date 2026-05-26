#!/usr/bin/env bash
set -euo pipefail

: "${DB_HOST:?DB_HOST is required}"
: "${DB_PORT:=3306}"
: "${DB_NAME:?DB_NAME is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${BACKUP_DIR:=./backups}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 127
  fi
}

if [[ ! "$DB_PORT" =~ ^[0-9]+$ ]] || (( DB_PORT < 1 || DB_PORT > 65535 )); then
  echo "DB_PORT must be an integer between 1 and 65535." >&2
  exit 2
fi

if [[ ! "$DB_NAME" =~ ^[A-Za-z0-9_][A-Za-z0-9_-]*$ ]]; then
  echo "DB_NAME may contain only letters, numbers, underscores, and hyphens, and must not start with a hyphen." >&2
  exit 2
fi

require_command mysqldump
require_command gzip
require_command sha256sum
require_command awk

umask 077
mkdir -p "$BACKUP_DIR"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
target="$BACKUP_DIR/${DB_NAME}-${timestamp}.sql.gz"
tmp_target="${target}.tmp"

cleanup() {
  rm -f "$tmp_target"
}
trap cleanup EXIT

if [[ -e "$target" || -e "$target.sha256" ]]; then
  echo "Refusing to overwrite existing backup files for timestamp $timestamp." >&2
  exit 1
fi

MYSQL_PWD="$DB_PASSWORD" mysqldump \
  --host="$DB_HOST" \
  --port="$DB_PORT" \
  --user="$DB_USERNAME" \
  --single-transaction \
  --quick \
  --routines \
  --triggers \
  --hex-blob \
  --default-character-set=utf8mb4 \
  --set-gtid-purged=OFF \
  "$DB_NAME" | gzip -9 > "$tmp_target"

checksum="$(sha256sum "$tmp_target" | awk '{print $1}')"
mv "$tmp_target" "$target"
(
  cd "$(dirname "$target")"
  printf '%s  %s\n' "$checksum" "$(basename "$target")" > "$(basename "$target").sha256"
)
echo "Created backup: $target"
