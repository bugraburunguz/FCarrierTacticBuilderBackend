-- BE-BUG-16: ingestion tek koşu kilidi DB'de (JVM AtomicBoolean yerine), heartbeat ile bayat koşu devri, siteden kalkan oyuncunun emekliye ayrılması.
ALTER TABLE ingestion_run ADD COLUMN heartbeat_at TIMESTAMPTZ NOT NULL DEFAULT now();
-- Eski sürümden kalan RUNNING satırları (çökmüş koşular) kısmi tekil indeks kurulmadan önce FAILED yapılır; veri silinmez.
UPDATE ingestion_run SET status = 'FAILED', last_error = COALESCE(last_error, 'interrupted before V26'), finished_at = COALESCE(finished_at, now()) WHERE status = 'RUNNING';
CREATE UNIQUE INDEX ux_ingestion_running ON ingestion_run (provider) WHERE status = 'RUNNING';

ALTER TABLE base_player ADD COLUMN last_seen_run BIGINT;
ALTER TABLE base_player ADD COLUMN retired_at TIMESTAMPTZ;
