-- BE-BUG-14: snapshot yalnız OVR/POT/takım tutmuyor; mevki, nitelikler, değer, maaş ve yaş da saklanır.
-- Eski satırlar bu kolonlarda NULL kalır (veri silinmez). game_date LE export v2 context.gameDate'ten gelir (LE-01/02), şimdilik NULL.
ALTER TABLE career_snapshot ADD COLUMN game_date DATE;
ALTER TABLE career_snapshot_player ADD COLUMN positions TEXT[];
ALTER TABLE career_snapshot_player ADD COLUMN attrs JSONB;
ALTER TABLE career_snapshot_player ADD COLUMN value_eur BIGINT;
ALTER TABLE career_snapshot_player ADD COLUMN wage_eur BIGINT;
ALTER TABLE career_snapshot_player ADD COLUMN age SMALLINT;
