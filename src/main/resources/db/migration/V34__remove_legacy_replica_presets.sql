-- REP-04: 34 eski REPLICA preset'i yeni 106 replika seed'iyle (seed/replicas.fc27.json) değişti; eşleme seed/replica-legacy-ids.json.
-- Statik seed satırları (kullanıcı verisi değil; kullanıcı verisinde bu id'lere başvuru yok: dry-run 34 satır / 0 başvuru).
-- Silmeden önce yedek: backups/preset_replica_before_delete_20261014.csv (STYLE ve PERSONAL preset'lere dokunulmaz).
DELETE FROM preset WHERE kind = 'REPLICA';
