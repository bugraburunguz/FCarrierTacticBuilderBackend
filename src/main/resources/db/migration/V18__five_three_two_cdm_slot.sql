-- TAC-09: 5-3-2'nin orta slotu CM iken EA'nın f532'sine uydu (CDM + 2 CM). Seed dışı (PERSONAL) preset'lerde slot anahtarı CM -> CDM taşınır;
-- slot etiketleri CDM karşılığına çevrilir (karşılığı olmayan etiket düşer), rol ipucu aynen taşınır.
UPDATE preset
SET slot_tags = (slot_tags - 'CM') || jsonb_build_object('CDM', COALESCE((
      SELECT jsonb_agg(CASE t WHEN 'cm_deep_build' THEN 'cdm_deep_build' ELSE 'cdm_destroyer' END)
      FROM jsonb_array_elements_text(slot_tags -> 'CM') t WHERE t IN ('cm_deep_build', 'cm_ball_winner')), '[]'::jsonb))
WHERE formation = '5-3-2' AND slot_tags ? 'CM';

UPDATE preset
SET role_hints = (role_hints - 'CM') || jsonb_build_object('CDM', role_hints -> 'CM')
WHERE formation = '5-3-2' AND role_hints ? 'CM';
