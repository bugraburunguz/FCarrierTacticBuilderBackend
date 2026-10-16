#!/usr/bin/env bash
# DEPLOY-01 4: yerel docker compose Postgres → Railway Postgres (tek seferlik taşıma).
#
#   TARGET_DATABASE_URL='postgresql://…' scripts/deploy/restore-to-railway.sh
#
# - Hedef adres YALNIZ env'den okunur (TARGET_DATABASE_URL); argüman almaz, ekrana/loga yazmaz. Docker'a `-e TARGET_DATABASE_URL`
#   ile değeri komut satırına koymadan aktarılır. `set -x` kullanılmaz.
# - Kaynak: bu repodaki compose'un postgres servisi. Döküm: pg_dump -Fc (postgres:16 istemcisi), git dışı ../backups altına.
# - Hedef boş değilse (public şemada tablo varsa) durur. Geri yükleme: pg_restore --no-owner --no-privileges --single-transaction.
# - Sonda kaynak ve hedefte her tablonun satır sayısı karşılaştırılır; fark varsa çıkış kodu 1.
set -euo pipefail

cd "$(dirname "$0")/../.."
: "${TARGET_DATABASE_URL:?TARGET_DATABASE_URL tanımlı değil (Railway Postgres DATABASE_PUBLIC_URL değerini yalnız bu oturumda env olarak ver)}"
case "$TARGET_DATABASE_URL" in
  postgres://*|postgresql://*) ;;
  *) echo "TARGET_DATABASE_URL postgres:// ya da postgresql:// ile başlamalı" >&2; exit 2 ;;
esac

CLIENT_IMAGE=postgres:16
BACKUP_DIR=../backups
STAMP=$(date +%Y%m%d-%H%M%S)
DUMP="$BACKUP_DIR/railway_migrate_$STAMP.dump"
mkdir -p "$BACKUP_DIR"

target_psql() {
  docker run --rm -i -e TARGET_DATABASE_URL "$CLIENT_IMAGE" sh -c 'psql "$TARGET_DATABASE_URL" -v ON_ERROR_STOP=1 -At "$@"' sh "$@"
}
source_psql() {
  docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -At "$@"' sh "$@"
}

echo "1/5 Hedef kontrolü"
echo "    sunucu sürümü: $(target_psql -c 'SHOW server_version')"
TABLES_IN_TARGET=$(target_psql -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'")
if [ "$TABLES_IN_TARGET" != "0" ]; then
  echo "Hedef boş değil (public şemada $TABLES_IN_TARGET tablo). Durdu; hiçbir şey yazılmadı." >&2
  exit 3
fi

echo "2/5 Kaynak döküm (pg_dump -Fc) → $DUMP"
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$DUMP"
echo "    boyut: $(du -h "$DUMP" | cut -f1)"

echo "3/5 Hedefe geri yükleme (pg_restore --no-owner --no-privileges --single-transaction)"
docker run --rm -i -e TARGET_DATABASE_URL "$CLIENT_IMAGE" \
  sh -c 'pg_restore --no-owner --no-privileges --single-transaction --exit-on-error -d "$TARGET_DATABASE_URL"' < "$DUMP"

echo "4/5 Satır sayıları"
COUNT_SQL="SELECT string_agg(format('SELECT %L AS t, count(*) AS n FROM public.%I', tablename, tablename), ' UNION ALL ' ORDER BY tablename) FROM pg_tables WHERE schemaname = 'public'"
QUERY=$(source_psql -c "$COUNT_SQL")
SOURCE_COUNTS=$(source_psql -F '|' -c "$QUERY ORDER BY 1")
TARGET_COUNTS=$(target_psql -F '|' -c "$QUERY ORDER BY 1")

echo "5/5 Karşılaştırma"
if diff <(echo "$SOURCE_COUNTS") <(echo "$TARGET_COUNTS") > /dev/null; then
  echo "$SOURCE_COUNTS" | awk -F'|' '{ total += $2 } END { printf "    %d tablo, %d satır: kaynak = hedef\n", NR, total }'
  echo "Tamam. Döküm: $DUMP (git dışı; taşıma doğrulanınca saklama süresine karar ver)."
else
  echo "UYUŞMAZLIK (tablo | kaynak | hedef):" >&2
  join -t '|' <(echo "$SOURCE_COUNTS") <(echo "$TARGET_COUNTS") | awk -F'|' '$2 != $3' >&2
  exit 1
fi
