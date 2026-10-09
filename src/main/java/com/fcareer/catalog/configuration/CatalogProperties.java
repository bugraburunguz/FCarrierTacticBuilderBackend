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
    private String faceUrlTemplate = "https://ratings-images-prod.pulse.ea.com/FC27/full/player-portraits/p{id}.png";
}
