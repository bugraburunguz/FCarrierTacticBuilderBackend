-- E3-7b: etiket tabanlı eski kural motoru kaldırıldı (kurallar artık seed/tactic-rules.fc27.json + analiz motoru).
-- tactic_rule statik seed tablosuydu (kullanıcı verisi değil); düşürülmeden önce yedeği alındı: backups/tactic_rule_before_drop_20261014-1338.sql (Buğra onaylı).
DROP TABLE IF EXISTS tactic_rule;
