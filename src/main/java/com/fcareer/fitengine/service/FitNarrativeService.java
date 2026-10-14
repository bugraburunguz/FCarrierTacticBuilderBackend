package com.fcareer.fitengine.service;

import com.fcareer.common.service.MessageService;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.response.FitNarrative;
import com.fcareer.fitengine.service.FitEngineV3.Contribution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Rol uyumu anlatımı (E3-8): her cümle yalnız bir katkının (term/key/points) ve kanıt sayılarının (rol puanı, levelFit, örneklem) şablonudur; serbest metin yoktur.
 * Cümlenin sayısal kaynağı log'lanır. "Biçilmiş kaftan" yalnız levelFit >= tailoredFrom (config, varsayılan 85, verified:false). Öneriler, analiz ve oyuncu sayfası aynı sözlüğü kullanır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FitNarrativeService {

    private static final int STRENGTHS = 3;
    private static final int WEAKNESSES = 2;
    private static final double ROUND = 10.0;

    private final MessageService messages;
    private final FitEngineProperties properties;

    public FitNarrative narrate(String roleName, double currentFit, Double levelFit, Integer sample, String fitLabel, List<Contribution> contributions) {
        boolean tailored = Objects.nonNull(levelFit) && levelFit >= properties.getV3().getTailoredFrom();
        List<FitNarrative.Sentence> strengths = contributions.stream().filter((c) -> c.points() > 0).sorted(Comparator.comparingDouble(Contribution::points).reversed()).limit(STRENGTHS)
                .map(this::sentence).toList();
        List<FitNarrative.Sentence> weaknesses = contributions.stream().filter((c) -> c.points() < 0).sorted(Comparator.comparingDouble(Contribution::points)).limit(WEAKNESSES)
                .map(this::sentence).toList();
        FitNarrative narrative = FitNarrative.builder().headline(headline(currentFit, levelFit, sample, tailored)).tailored(tailored).strengths(strengths).weaknesses(weaknesses).build();
        log.debug("NLG source role : {} currentFit : {} levelFit : {} sample : {} label : {} tailored : {} strengths : {} weaknesses : {}", roleName, currentFit, levelFit, sample, fitLabel, tailored,
                source(strengths), source(weaknesses));
        return narrative;
    }

    private String headline(double currentFit, Double levelFit, Integer sample, boolean tailored) {
        String points = String.valueOf(Math.round(currentFit));
        if (Objects.isNull(levelFit)) {
            return messages.getMessage("nlg.v3.headline.noLevel", points);
        }
        String key = tailored ? "nlg.v3.headline.tailored" : "nlg.v3.headline";
        return messages.getMessage(key, points, String.valueOf(Math.round(levelFit)), String.valueOf(Objects.isNull(sample) ? 0 : sample));
    }

    private FitNarrative.Sentence sentence(Contribution c) {
        double rounded = Math.round(c.points() * ROUND) / ROUND;
        String sign = c.points() >= 0 ? "pos" : "neg";
        String number = String.format(Locale.ROOT, "%.1f", Math.abs(c.points()));
        String label = switch (c.term()) {
            case "attr", "base" -> Attr.fromKey(c.key()).map((a) -> messages.getMessage(a.getMessageKey())).orElse(c.key());
            case "playstyle" -> messages.getMessage("ps." + c.key());
            case "accelerate" -> messages.getMessage("accelerate." + c.key());
            default -> c.key();
        };
        String text = messages.getMessage("nlg.v3." + c.term() + "." + sign, label, number);
        return FitNarrative.Sentence.builder().text(text).term(c.term()).key(c.key()).points(rounded).build();
    }

    private static String source(List<FitNarrative.Sentence> sentences) {
        return sentences.stream().map((s) -> s.getTerm() + ":" + s.getKey() + "=" + String.format(Locale.ROOT, "%.1f", s.getPoints())).collect(Collectors.joining(","));
    }
}
