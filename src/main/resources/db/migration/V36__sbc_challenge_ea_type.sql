-- V3-05d-b: SBC görevinde EA'nın ham formasyon kodu ve görev tipi saklanır; tür kümesine SINGLE_ITEM (tek kart teslimi) eklenir.
-- Veri silinmez: yalnız iki sütun eklenir ve CHECK kısıtı genişletilir (mevcut SQUAD / ITEM_SCORE değerleri geçerli kalır).
ALTER TABLE sbc_challenge ADD COLUMN ea_formation TEXT;
ALTER TABLE sbc_challenge ADD COLUMN ea_type TEXT;
ALTER TABLE sbc_challenge DROP CONSTRAINT IF EXISTS sbc_challenge_kind_check;
ALTER TABLE sbc_challenge ADD CONSTRAINT sbc_challenge_kind_check CHECK (kind IN ('SQUAD', 'ITEM_SCORE', 'SINGLE_ITEM'));
