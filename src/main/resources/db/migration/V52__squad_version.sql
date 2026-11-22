-- FCA-27: baz kadro (katalog) sürümleri. En yeni sürüm canlı tablolarda (player_rating, club_profile) kalır;
-- yeni BASE yüklemesi öncesi mevcut veri eski sürüm kimliğiyle arşive kopyalanır. Kariyer açıldığı sürüme bağlıdır.
-- SADECE EKLEME: mevcut satırlar silinmez, değiştirilmez (career'a yalnız yeni kolon doldurulur).
CREATE TABLE squad_version (
  id              BIGSERIAL    PRIMARY KEY,
  game_version_id SMALLINT     NOT NULL REFERENCES game_version(id),
  version_no      INT          NOT NULL,
  label           VARCHAR(120) NOT NULL,
  game_build      VARCHAR(40),
  exported_at     TIMESTAMPTZ,
  player_count    INT          NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  UNIQUE (game_version_id, version_no)
);

-- player_rating ile aynı kolonlar (id hariç, arşivin kendi kimliği var) + squad_version_id.
CREATE TABLE player_rating_archive (LIKE player_rating INCLUDING DEFAULTS);
ALTER TABLE player_rating_archive DROP COLUMN id;
ALTER TABLE player_rating_archive ADD COLUMN id BIGSERIAL PRIMARY KEY;
ALTER TABLE player_rating_archive ADD COLUMN squad_version_id BIGINT NOT NULL REFERENCES squad_version(id) ON DELETE CASCADE;
ALTER TABLE player_rating_archive ADD CONSTRAINT fk_rating_archive_player FOREIGN KEY (base_player_id) REFERENCES base_player(id);
ALTER TABLE player_rating_archive ADD CONSTRAINT ux_rating_archive UNIQUE (squad_version_id, base_player_id);
CREATE INDEX ix_rating_archive_version_club ON player_rating_archive(squad_version_id, club_id);

CREATE TABLE club_profile_archive (LIKE club_profile INCLUDING DEFAULTS);
ALTER TABLE club_profile_archive ADD COLUMN squad_version_id BIGINT NOT NULL REFERENCES squad_version(id) ON DELETE CASCADE;
ALTER TABLE club_profile_archive ADD CONSTRAINT fk_club_profile_archive_club FOREIGN KEY (club_id) REFERENCES club(id) ON DELETE CASCADE;
ALTER TABLE club_profile_archive ADD PRIMARY KEY (squad_version_id, club_id);

ALTER TABLE dataset_version ADD COLUMN squad_version_id BIGINT NULL REFERENCES squad_version(id);

-- Sürüm 1: her oyun sürümü için (katalog boş olsa da) kariyer açılırken bağlanacak bir sürüm bulunur.
INSERT INTO squad_version (game_version_id, version_no, label, player_count)
SELECT g.id, 1, 'İlk kadro (Sürüm 1)',
       (SELECT count(*) FROM player_rating r JOIN base_player b ON b.id = r.base_player_id
         WHERE r.game_version_id = g.id AND b.owner_career_id IS NULL)
FROM game_version g;

ALTER TABLE career ADD COLUMN squad_version_id BIGINT REFERENCES squad_version(id);
UPDATE career c SET squad_version_id = s.id
FROM squad_version s
WHERE s.version_no = 1
  AND s.game_version_id = COALESCE(c.game_version_id, (SELECT min(id) FROM game_version));
ALTER TABLE career ALTER COLUMN squad_version_id SET NOT NULL;
CREATE INDEX ix_career_squad_version ON career(squad_version_id);
