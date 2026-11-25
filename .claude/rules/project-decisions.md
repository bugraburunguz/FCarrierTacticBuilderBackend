# FC Assist proje kararları (backend)

Takım kurallarında "(proje kararı)" diye işaretli konuların FC Assist backend'indeki karşılığı. Ayrıntı: `docs/fcareer-standard.md`.

- Yol: tek kanal, `/api/v1/...` (`ApiConstants.API_V1`). Web/mobil kanal ayrımı yok.
- Yanıt zarfı: `ApiResponse<T>` (`success`, `data`, `message`, `error`); `SuccessResponseAdvice` sarar, controller ham nesne döner.
- Hata taşıma: HTTP status kodu (D-09). 400 doğrulama, 401 oturum, 402 kredi, 403 yetki, 404 yok (başkasının kaynağı da 404), 409 çakışma, 413 gövde, 422 iş kuralı, 426 eski extension, 429 limit, 503 devre dışı. 200 + hata kodu biçimi kullanılmaz. `GlobalExceptionAdvice` çevirir.
- Saklama saat dilimi: UTC (`Clock.systemUTC()`). İçerik takvimi Europe/London, gösterim Europe/Istanbul; dönüşüm sınırda.
- Testler zorunlu: kabul kriteri önce kırmızı test olarak yazılır. İlgili test sınıfı yoksa yeni test sınıfı açılabilir, sorulmaz.
