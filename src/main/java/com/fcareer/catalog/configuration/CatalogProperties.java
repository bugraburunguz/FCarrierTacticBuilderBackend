package com.fcareer.catalog.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.catalog")
public class CatalogProperties {
    private short defaultGameVersion = 27;
    /** Oyuncu yüzü için içerik adresi şablonu; {id} EA oyuncu kimliğiyle değişir. Boşsa yüz adresi dönülmez (takedown anahtarı). */
    /** Yanıtlardaki yüz adresi: kendi proxy'miz ({id} = EA oyuncu kimliği). Şablon boşsa yüz adresi dönülmez. */
    private String faceProxyPath = "/api/v1/img/face/{id}";
    /** Boş: FC27 portre yolu EA tarafında 403/404 veriyor (görüntü yüklenemiyor, konsol hatası). CARD-01 doğru kaynağı getirene kadar kapalı. */
    private String faceUrlTemplate = "";
    /**
     * Oyuncu kartı görseli kaynağı ({id} = EA oyuncu kimliği); sunucu üzerinden /api/v1/img/card/{id} ile sunulur. Boş: kapalı (web kendi kartını
     * çizer). Kaynak Buğra onayıyla (EA reyting görselleri, tr-TR).
     */
    private String cardImageUrlTemplate = "https://ratings-images-prod.pulse.ea.com/FC27/components/items/{id}_tr-TR.webp";
}
