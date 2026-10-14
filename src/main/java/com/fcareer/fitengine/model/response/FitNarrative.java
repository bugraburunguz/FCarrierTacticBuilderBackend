package com.fcareer.fitengine.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Rol uyumu anlatımı (E3-8): her cümle yalnız bir katkının (term/key/points) ve kanıt sayılarının şablonudur; `Sentence` cümlenin sayısal kaynağını taşır.
 * tailored ("Biçilmiş kaftan") yalnız levelFit eşiğini (config, varsayılan 85) geçince true.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FitNarrative {

    private String headline;
    private boolean tailored;
    private List<Sentence> strengths;
    private List<Sentence> weaknesses;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sentence {
        private String text;
        private String term;
        private String key;
        private double points;
    }
}
