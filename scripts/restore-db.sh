#!/usr/bin/env bash
set -euo pipefail

: "${DB_HOST:?DB_HOST is required}"
: "${DB_PORT:=3306}"
: "${DB_NAME:?DB_NAME is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 127
  fi
}

read_checksum_digest() {
  local checksum_file="$1"
  local -a checksum_lines=()
  local checksum_line=""
  local digest=""
  local rest=""

  mapfile -t checksum_lines < "$checksum_file"
  if (( ${#checksum_lines[@]} != 1 )); then
    echo "Checksum file must contain exactly one entry: $checksum_file" >&2
    exit 2
  fi

  checksum_line="${checksum_lines[0]}"
  IFS=' ' read -r digest rest <<< "$checksum_line"
  if [[ -z "$digest" || -z "$rest" || ! "$digest" =~ ^[0-9a-fA-F]{64}$ ]]; then
    echo "Invalid checksum file format: $checksum_file" >&2
    exit 2
  fi

  printf '%s\n' "${digest,,}"
}

backup_file="${1:-}"
if [[ -z "$backup_file" || ! -f "$backup_file" ]]; then
  echo "Usage: $0 /path/to/${DB_NAME}-backup.sql.gz" >&2
  exit 2
fi

require_command mysql
require_command gzip
require_command sha256sum

if [[ ! "$DB_PORT" =~ ^[0-9]+$ ]] || (( DB_PORT < 1 || DB_PORT > 65535 )); then
  echo "DB_PORT must be an integer between 1 and 65535." >&2
  exit 2
fi

if [[ ! "$DB_NAME" =~ ^[A-Za-z0-9_][A-Za-z0-9_-]*$ ]]; then
  echo "DB_NAME may contain only letters, numbers, underscores, and hyphens, and must not start with a hyphen." >&2
  exit 2
fi

backup_dir="$(cd "$(dirname "$backup_file")" && pwd -P)"
backup_name="$(basename "$backup_file")"
backup_path="$backup_dir/$backup_name"
checksum_path="$backup_path.sha256"

gzip -t -- "$backup_path"

if [[ -f "$checksum_path" ]]; then
  expected_checksum="$(read_checksum_digest "$checksum_path")"
  actual_checksum="$(sha256sum "$backup_path")"
  actual_checksum="${actual_checksum%% *}"

  if [[ "${actual_checksum,,}" != "$expected_checksum" ]]; then
    echo "Checksum verification failed for $backup_path" >&2
    exit 1
  fi
elif [[ "${RESTORE_ALLOW_UNVERIFIED:-false}" != "true" ]]; then
  echo "Checksum file not found: $checksum_path" >&2
  echo "Set RESTORE_ALLOW_UNVERIFIED=true to restore without checksum verification." >&2
  exit 2
fi

read -r -p "Restore $backup_path into $DB_HOST:$DB_PORT/$DB_NAME? Type RESTORE to continue: " confirmation
if [[ "$confirmation" != "RESTORE" ]]; then
  echo "Restore cancelled."
  exit 1
fi

MYSQL_PWD="$DB_PASSWORD" mysql \
  --host="$DB_HOST" \
  --port="$DB_PORT" \
  --user="$DB_USERNAME" \
  --binary-mode=1 \
  --default-character-set=utf8mb4 \
  "$DB_NAME" < <(gzip -dc -- "$backup_path")

echo "Restore completed into $DB_NAME."
