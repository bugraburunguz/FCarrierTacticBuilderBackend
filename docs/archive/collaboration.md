# Claude ile Çalışma Notları — İletişim & Süreç

> Bu doküman **kod standardı değil**; Buğra ↔ asistan çalışma biçimidir: ticket nasıl verilir, Jira yorumu nasıl yazılır, ne zaman soru sorulur.
> Kod/review kuralları için → `backend-standard.md`. (Eskiden burada olan review teknikleri — `git log -S` doğrulaması, cross-service twin kontrolü, mevcut alanı görmeden şema önerme — oraya, Bölüm 1.7 ve 2'ye taşındı.)

---

## 1. Ticket verirken — turu kısaltan alışkanlıklar

- **Ticket metni + kapsam fiili aynı mesajda.** "İncele, kod yazma" / "uygula" / "yorum yaz" gibi net bir fiil en hızlı yolu açar. Kapsam yoksa asistan "kod mu yazayım, sadece mi bakayım" diye sormak zorunda kalır.
- **Bildiğin çelişki/override'ı önden söyle.** "Maliyye statüsü zaten var" gibi bir şeyi asistan bulup soru olarak geri getirmeden söylersen bir tur kazanılır.
- **Numaralı cevap.** Birden fazla açık soruyu "1... 2... 3..." sırayla cevapla; asistan aynı sırayla eşler.
- **Erteleme tek seferlik.** "Testten dönerse bakarız" dedikten sonra o konu tekrar açılmaz; hatırlatmaya gerek yok.
- **Cross-service şüpheni söyle.** "Bu user-admin'i de etkiliyor olabilir, emin değilim" doğru refleks (AZ-10477, AZ-10482'de haklı çıktı). Asistan bunu artık otomatik kontrol ediyor (standart Bölüm 1.7 twin kuralı), ama sen yine de şüphelendiğinde söyle.

---

## 2. Jira yorumu — format

Sabit format, süslemesiz sadece somut bilgi:

**Endpoint/Service — Exceptions — Enums — Tablo (kayıt atılan)**

- Süreç anlatımı yok ("şu ertelendi", "bu senin kararınla" vb.).
- Ertelenen/yapılmayan madde yorumu yok.
- Gerekçe/motivasyon cümlesi yok.

---

## 3. Soru disiplini — ne zaman blocking soru?

- Esas: ticket'ın **"User Requirements" / "Kural"** kısmındaki net maddeler.
- Yorum thread'indeki yan/belirsiz notlar (ör. "fin kod + serial number'ın numerik kısımları...") sadece kısa dipnot geçilir; ana gereksinimde gerçek belirsizlik yoksa blocking soru yapılmaz.
- Gerçek belirsizlik varsa **tek** net soru sor, iş yapılabilir kısmı bekletme.

---

## 4. Ticket paylaşırken hızlı checklist

- [ ] Ticket metni
- [ ] Kapsam: "sadece incele" / "uygula" / "yorum yaz"
- [ ] Bildiğin çelişki/override → 1 cümle
- [ ] Ertelenecek madde → "X testten dönerse bakarız" (tek sefer)
