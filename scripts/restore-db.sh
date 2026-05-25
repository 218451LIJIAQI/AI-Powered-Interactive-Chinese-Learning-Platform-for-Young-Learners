#!/usr/bin/env bash
set -euo pipefail

: "${DB_HOST:?DB_HOST is required}"
: "${DB_PORT:=3306}"
: "${DB_NAME:?DB_NAME is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

backup_file="${1:-}"
if [[ -z "$backup_file" || ! -f "$backup_file" ]]; then
  echo "Usage: $0 /path/to/${DB_NAME}-backup.sql.gz" >&2
  exit 2
fi

if [[ -f "$backup_file.sha256" ]]; then
  sha256sum --check "$backup_file.sha256"
fi

read -r -p "Restore $backup_file into $DB_HOST:$DB_PORT/$DB_NAME? Type RESTORE to continue: " confirmation
if [[ "$confirmation" != "RESTORE" ]]; then
  echo "Restore cancelled."
  exit 1
fi

MYSQL_PWD="$DB_PASSWORD" mysql \
  --host="$DB_HOST" \
  --port="$DB_PORT" \
  --user="$DB_USERNAME" \
  "$DB_NAME" < <(gzip -dc "$backup_file")

echo "Restore completed into $DB_NAME."
