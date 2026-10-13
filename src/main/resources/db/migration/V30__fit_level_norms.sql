-- levelFit dağılımları (13 §3.2 madde 11): (cinsiyet, mevki, rol, focus, OVR) için OVR bandındaki katalog oyuncularının currentFit yüzdelik noktaları.
-- Oyuncu kataloğundan yeniden üretilebilir (FitLevelNormsBuilder); band genişliği ve örnek sayısı kayıt altındadır.
CREATE TABLE fit_level_norms (
  gender         SMALLINT    NOT NULL CHECK (gender IN (0, 1)),
  position       VARCHAR(4)  NOT NULL,
  role_id        VARCHAR(40) NOT NULL,
  focus          VARCHAR(20) NOT NULL,
  ovr            SMALLINT    NOT NULL,
  engine_version VARCHAR(8)  NOT NULL,
  band_lo        SMALLINT    NOT NULL,
  band_hi        SMALLINT    NOT NULL,
  sample_count   INTEGER     NOT NULL,
  quantiles      REAL[]      NOT NULL,
  built_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (gender, position, role_id, focus, ovr, engine_version)
);
