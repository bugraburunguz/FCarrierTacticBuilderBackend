-- KARIYER-UX FAZ 2 (15 §7.2): Masa finans kutucuklarının Δ'sı için snapshot anındaki transfer bütçesi ve kadro maaş toplamı.
-- squad_wage_eur kadroda maaşı bilinmeyen oyuncu varsa NULL kalır. Eski snapshot'lar NULL (Δ gösterilmez).
ALTER TABLE career_snapshot ADD COLUMN budget_eur BIGINT;
ALTER TABLE career_snapshot ADD COLUMN squad_wage_eur BIGINT;
