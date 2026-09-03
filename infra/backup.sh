#!/usr/bin/env bash
# Nightly dump of the asr database. Installed at /opt/asr/backup.sh, run from cron.
set -euo pipefail

cd /opt/asr
mkdir -p backups
stamp=$(date -u +%Y%m%d-%H%M%S)
out="backups/db-$stamp.dump"

docker compose exec -T postgres pg_dump -U asr -d asr -Fc > "$out"
test -s "$out"

# keep the last 14 nightly dumps
ls -t backups/db-*.dump | tail -n +15 | xargs -r rm

# Offsite copy: same bucket/tooling as Bookween, under an asr/ prefix.
# Uncomment once R2 credentials are in /opt/asr/.env.
# set -a; . /opt/asr/.env; set +a
# aws s3 cp "$out" "s3://$R2_BACKUP_BUCKET/asr/" --endpoint-url "$R2_ENDPOINT"

echo "asr backup ok: $out"
