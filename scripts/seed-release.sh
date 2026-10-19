#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 4 ]]; then
    printf 'Usage: make release-seed|dev-seed [ADB=adb] [DEVICE=serial] [ANCHOR_DATE=YYYY-MM-DD]\n' >&2
    exit 2
fi

adb_bin=$1
device=$2
package_name=$3
anchor_date=$4

if ! command -v "${adb_bin}" >/dev/null 2>&1; then
    printf 'ERROR: adb executable not found: %s\n' "${adb_bin}" >&2
    exit 1
fi

if [[ ! "${anchor_date}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
    printf 'ERROR: ANCHOR_DATE must use YYYY-MM-DD: %s\n' "${anchor_date}" >&2
    exit 2
fi

parsed_anchor=$(date -u -d "${anchor_date}" +%F 2>/dev/null || true)
if [[ "${parsed_anchor}" != "${anchor_date}" ]]; then
    printf 'ERROR: invalid ANCHOR_DATE: %s\n' "${anchor_date}" >&2
    exit 2
fi

adb_command=("${adb_bin}")
if [[ -n "${device}" ]]; then
    adb_command+=(-s "${device}")
fi

if ! "${adb_command[@]}" get-state >/dev/null 2>&1; then
    printf 'ERROR: no ready adb device found.\n' >&2
    exit 1
fi

package_path=$("${adb_command[@]}" shell pm path "${package_name}" 2>/dev/null || true)
if [[ -z "${package_path//$'\r'/}" ]]; then
    printf 'ERROR: package is not installed: %s\n' "${package_name}" >&2
    printf 'Hint: run make run (debug) or install the release APK first.\n' >&2
    exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output_dir="${repo_root}/build/demo-data"
backup_file="${output_dir}/endoluteal-demo-backup.json"

printf '==> Generating demo backup (anchor=%s)...\n' "${anchor_date}"
(
    cd "${repo_root}"
    ANCHOR_DATE="${anchor_date}" ./gradlew :app:testDebugUnitTest \
        --tests fr.luteal.demo.DemoBackupExportTest \
        -q
)

if [[ ! -f "${backup_file}" ]]; then
    backup_file="${repo_root}/app/build/demo-data/endoluteal-demo-backup.json"
fi
if [[ ! -f "${backup_file}" ]]; then
    printf 'ERROR: backup was not written under build/demo-data\n' >&2
    exit 1
fi

printf '==> Generated demo backup: %s\n' "${backup_file}"

device_backup_path="/data/local/tmp/endoluteal-demo-backup.json"
printf '==> Pushing backup to device (%s)...\n' "${device_backup_path}"
"${adb_command[@]}" push "${backup_file}" "${device_backup_path}"
"${adb_command[@]}" shell chmod 644 "${device_backup_path}" 2>/dev/null || true

printf '==> Opening Endoluteal backup import preview...\n'
"${adb_command[@]}" shell am force-stop "${package_name}"
"${adb_command[@]}" shell am start -W \
    -n "${package_name}/fr.luteal.app.AdbBackupImportActivity" \
    -t application/json \
    --es "fr.luteal.app.extra.IMPORT_JSON_PATH" "${device_backup_path}"

printf '%s\n' 'Import preview is open. Confirm « Remplacer tout » to load demo data.'
printf 'Manual import: Réglages → Données → Importer → %s\n' "${backup_file}"
