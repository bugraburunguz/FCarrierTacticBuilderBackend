-- KARIYER-UX FAZ 1 (D-30): ana taktik sunucuda. Taslaktan kayıt (tek taktik gövdesi TacticRequest) + opsiyonel diziliş.
-- slots JSONB satırları {slot, position, roleId, focus, tags[]} (tags ayrı kolon değil; replika kayıtlarında boş dizi).
-- lineup: {"slotId": base_player.id} (/fit/lineup'ın döndürdüğü playerId). Boşsa /fit/lineup dizer.
ALTER TABLE user_tactic
  ADD COLUMN is_main    BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN lineup     JSONB,
  ADD COLUMN depth      SMALLINT CHECK (depth BETWEEN 0 AND 100),
  ADD COLUMN preset_id  TEXT,
  ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Kullanıcı + kariyer başına en fazla bir ana taktik. UT'de (career_id null) ana taktik yok.
CREATE UNIQUE INDEX ux_user_tactic_main ON user_tactic (user_id, career_id) WHERE is_main;
ALTER TABLE user_tactic ADD CONSTRAINT ck_user_tactic_main_career CHECK (NOT is_main OR career_id IS NOT NULL);
