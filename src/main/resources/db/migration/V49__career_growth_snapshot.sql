-- GTR-a: gelişim/düşüş takibi için kariyer içi içe aktarma anlık görüntüsü. Export'taki bütün kulüplerin oyuncuları (EA oyuncu kimliğiyle; transferde
-- sabit) tek satırda, gzip'li kompakt ikili olarak (GrowthSnapshotCodec, format 1): oyuncu başına satır (~150 B × 22 bin ≈ 4 MB/görüntü) yerine
-- görüntü başına ~0,86 MB (ölçülen, 22.239 oyuncu); kariyer başına en çok ~52 MB. Aynı oyun ayına düşen içe aktarmalardan yalnız sonuncusu
-- (career_id, month_key tekil); kariyer başına en çok 60, aşılırsa en eskiler seyreltilir (uygulamada). save_uid: export'un save kimliği (başka
-- save'den içe aktarma loglanır). Kariyer silinince görüntüler de silinir.
CREATE TABLE career_growth_snapshot (
  id           BIGSERIAL   PRIMARY KEY,
  career_id    BIGINT      NOT NULL REFERENCES career(id) ON DELETE CASCADE,
  game_date    DATE        NOT NULL,
  month_key    INTEGER     NOT NULL,
  save_uid     TEXT,
  player_count INTEGER     NOT NULL,
  format       SMALLINT    NOT NULL DEFAULT 1,
  data         BYTEA       NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT career_growth_snapshot_month UNIQUE (career_id, month_key)
);
