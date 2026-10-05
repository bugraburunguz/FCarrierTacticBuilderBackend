package com.fcareer.ingestion.service;

import com.fcareer.catalog.entity.BasePlayerEntity;
import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.catalog.mapper.RatingAttrAccessor;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.PlayerRatingRepository;
import com.fcareer.common.exception.ImportParseException;
import com.fcareer.fitengine.model.AcceleRate;
import com.fcareer.ingestion.mapper.AcceleRateDeriver;
import com.fcareer.ingestion.model.EnrichmentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogEnrichmentService {

    private static final String PLAYER_ID = "playerid";
    private static final String HEIGHT = "height";
    private static final String WEIGHT = "weight";
    private static final String RUN_STYLE_1 = "runningcode1";
    private static final String RUN_STYLE_2 = "runningcode2";
    private static final int MIN_HEIGHT_CM = 140;
    private static final int MAX_HEIGHT_CM = 225;
    private static final int MIN_WEIGHT_KG = 40;
    private static final int MAX_WEIGHT_KG = 150;
    private static final int BATCH = 500;

    private final BasePlayerRepository basePlayerRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final RatingAttrAccessor ratingAttrAccessor;
    private final AcceleRateDeriver acceleRateDeriver;
    private final TransactionTemplate transactionTemplate;

    /**
     * Live Editor dökümündeki players.csv'den boy/kilo alır (EA FC27 sayfası bu alanları boş veriyor)
     * ve eşleşen oyuncuların AcceleRATE'ini yeniden hesaplar. Potential/değer gibi gizli değerler okunmaz.
     */
    public EnrichmentResult enrichFromPlayersCsv(InputStream csv, short gameVersionId) {
        Map<Long, int[]> measures = readMeasures(csv);
        int updated = 0;
        int accelerate = 0;
        List<Long> eaIds = new ArrayList<>(measures.keySet());
        for (int from = 0; from < eaIds.size(); from += BATCH) {
            List<Long> batch = eaIds.subList(from, Math.min(from + BATCH, eaIds.size()));
            int[] counts = transactionTemplate.execute(status -> apply(batch, measures, gameVersionId));
            updated += counts[0];
            accelerate += counts[1];
        }
        log.info("Catalog enriched rowsInFile : {} updated : {} accelerateSet : {}", measures.size(), updated, accelerate);
        return new EnrichmentResult(measures.size(), updated, accelerate);
    }

    private int[] apply(List<Long> eaIds, Map<Long, int[]> measures, short gameVersionId) {
        Map<Long, BasePlayerEntity> players = basePlayerRepository.findByEaResourceIdIn(eaIds).stream()
                .collect(Collectors.toMap(BasePlayerEntity::getEaResourceId, Function.identity()));
        Map<Long, PlayerRatingEntity> ratings = playerRatingRepository
                .findByBasePlayerIdInAndGameVersionId(players.values().stream().map(BasePlayerEntity::getId).toList(), gameVersionId)
                .stream().collect(Collectors.toMap(PlayerRatingEntity::getBasePlayerId, Function.identity()));
        int updated = 0;
        int accelerate = 0;
        for (BasePlayerEntity player : players.values()) {
            int[] measure = measures.get(player.getEaResourceId());
            if (measure[0] > 0) {
                player.setHeightCm((short) measure[0]);
            }
            if (measure[1] > 0) {
                player.setWeightKg((short) measure[1]);
            }
            basePlayerRepository.save(player);
            updated++;
            PlayerRatingEntity rating = ratings.get(player.getId());
            if (Objects.nonNull(rating)) {
                rating.setRunStyle1((short) measure[2]);
                rating.setRunStyle2((short) measure[3]);
                playerRatingRepository.save(rating);
            }
            if (Objects.nonNull(rating) && Objects.nonNull(player.getHeightCm())) {
                rating.setAccelerate(acceleRateDeriver.derive(player.getHeightCm().intValue(), player.getGender(),
                        ratingAttrAccessor.read(rating)).map(AcceleRate::name).orElse(null));
                playerRatingRepository.save(rating);
                accelerate++;
            }
        }
        return new int[]{updated, accelerate};
    }

    private Map<Long, int[]> readMeasures(InputStream csv) {
        Map<Long, int[]> measures = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (Objects.isNull(headerLine)) {
                throw new ImportParseException("empty file");
            }
            List<String> header = OpenDatasetImporter.splitLine(headerLine.replace("﻿", ""), ',');
            int idIndex = header.indexOf(PLAYER_ID);
            int heightIndex = header.indexOf(HEIGHT);
            int weightIndex = header.indexOf(WEIGHT);
            int run1Index = header.indexOf(RUN_STYLE_1);
            int run2Index = header.indexOf(RUN_STYLE_2);
            if (idIndex < 0 || heightIndex < 0) {
                throw new ImportParseException("columns playerid and height are required");
            }
            String line;
            while (Objects.nonNull(line = reader.readLine())) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = OpenDatasetImporter.splitLine(line, ',');
                Long id = parse(cells, idIndex);
                if (Objects.isNull(id)) {
                    continue;
                }
                measures.put(id, new int[]{
                        inRange(parse(cells, heightIndex), MIN_HEIGHT_CM, MAX_HEIGHT_CM),
                        weightIndex < 0 ? 0 : inRange(parse(cells, weightIndex), MIN_WEIGHT_KG, MAX_WEIGHT_KG),
                        run1Index < 0 ? 0 : inRange(parse(cells, run1Index), 0, 255),
                        run2Index < 0 ? 0 : inRange(parse(cells, run2Index), 0, 255)});
            }
        } catch (IOException e) {
            log.error("Enrichment file unreadable", e);
            throw new ImportParseException("file unreadable");
        }
        return measures;
    }

    private static Long parse(List<String> cells, int index) {
        if (index >= cells.size()) {
            return null;
        }
        try {
            return Long.parseLong(cells.get(index).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int inRange(Long value, int min, int max) {
        return Objects.nonNull(value) && value >= min && value <= max ? value.intValue() : 0;
    }
}
