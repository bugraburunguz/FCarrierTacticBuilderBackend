-- BE-BUG-07: lokalize ad/açıklama içerik hash'inden ayrı tutulur; aynı içerik TR ve EN katkıcıdan gelince çelişki sayılmaz.
CREATE TABLE kb_text (
  kind        TEXT NOT NULL,
  natural_key TEXT NOT NULL,
  locale      TEXT NOT NULL,
  name        TEXT,
  description TEXT,
  PRIMARY KEY (kind, natural_key, locale)
);
