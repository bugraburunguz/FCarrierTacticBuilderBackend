# KARIYER-03 — Kariyer kiralıkları (betik 2.0.3)

## Bulgu (teşhis betiği `fc27_db_probe.lua`, kullanıcının kariyer log'u 2026-10-18)
- Kariyer log'undaki `playerloans` ana menü kaydıyla **birebir aynı** (1426 satır, eklenen/silinen yok): oyun kariyerde yapılan kiralıkları
  hiçbir veritabanı tablosuna yazmıyor. Kullanıcının 9 kiralığından yalnız Lynch ve Keyrol (Figueroa) tabloda; ikisi de ana kayıtta zaten Port Vale'de kiralık.
- 2. veritabanı yalnız 4 metin/indeks tablosu taşıyor (kariyer verisi değil). `career_*` tabloları 1. veritabanında; `career_users`,
  `career_playercontract` FC27 meta'sında yok.
- `previousteam` kalıcı transferde oyuncunun ayrıldığı kulübü tutuyor: Román ve Gutiérrez → 111592 (serbest havuz). Kiralanan 7 oyuncunun satırı yok.
- Adsız tablolarda sütun genişliği (`sz`) meta derinliğiyle aynı (ölçüm satırı `CAL`); ama kimlik sütunlarında `min=-1` olabilir.

## Değişiklik
- Betik 2.0.3: kariyerde `previousteam` dışa aktarılır.
- İçe aktarma: kullanıcı takımına gelen ya da ondan giden oyuncu, kiralık tablosunda yoksa ve `previousteam` ana kayıttaki kulübünü
  göstermiyorsa kiralıktır (kaynak = ana kayıttaki kulüp). Serbest havuzdan gelen / serbest kalan / `previousteam`'i serbest havuz olan transferdir.
  `previousteam` tablosu olmayan eski log'larda davranış aynı.

## Doğrulama
- `CareerLoanSourcesIntegrationTest` (kiralık giriş/çıkış, transfer giriş/çıkış, serbestten transfer), `LeExportScriptTest` (2.0.3, previousteam).
- Gerçek kariyer log'u + probe'daki iki `previousteam` satırı yerelde: 9 kiralık LOAN_IN, Román/Gutiérrez TRANSFER_IN.
  Giden oyuncular bu denemede `previousteam` satırları olmadığı için LOAN_OUT çıktı; gerçek 2.0.3 log'unda satılanların satırı beklenir (doğrulanacak).

## Açık
- Kiralık bitiş tarihi ve satın alma opsiyonu kariyer kiralıkları için okunamıyor (tabloda yok).
- Kullanıcı 2.0.3 ile yeni log almalı; giden oyuncuların sınıflandırması o log'la doğrulanacak.
