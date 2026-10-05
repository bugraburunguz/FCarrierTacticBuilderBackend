package com.fcareer.ingestion.service;

import com.fcareer.catalog.entity.BasePlayerEntity;
import com.fcareer.catalog.entity.PlayerValuationEntity;
import com.fcareer.catalog.model.enums.ValuationSource;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.PlayerValuationRepository;
import com.fcareer.common.exception.ImportParseException;
import com.fcareer.ingestion.configuration.IngestionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenDatasetImporter {

    private static final String IMPORT_DETAIL_LICENSE = "open dataset license not confirmed (open_dataset_license)";
    private static final String IMPORT_DETAIL_MAPPING = "open dataset column mapping is not configured";
    private static final String MODEL_VERSION = "open-dataset-seed";

    private final IngestionProperties properties;
    private final BasePlayerRepository basePlayerRepository;
    private final PlayerValuationRepository playerValuationRepository;

    @Transactional
    public int importSeed() {
        IngestionProperties.OpenDataset config = properties.getOpenDataset();
        if (!config.isLicenseConfirmed()) {
            throw new ImportParseException(IMPORT_DETAIL_LICENSE);
        }
        if (config.getPath().isBlank() || config.getEaIdColumn().isBlank()
                || (config.getPotentialColumn().isBlank() && config.getValueColumn().isBlank())) {
            throw new ImportParseException(IMPORT_DETAIL_MAPPING);
        }
        List<Map<String, String>> rows = readRows(Path.of(config.getPath()), config.getDelimiter());
        Map<Long, BasePlayerEntity> players = basePlayerRepository
                .findByEaResourceIdIn(rows.stream().map(r -> parseLong(r.get(config.getEaIdColumn()))).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(BasePlayerEntity::getEaResourceId, Function.identity()));
        short gameVersion = properties.getGameVersionId();
        int written = 0;
        for (Map<String, String> row : rows) {
            Long eaId = parseLong(row.get(config.getEaIdColumn()));
            BasePlayerEntity player = Objects.isNull(eaId) ? null : players.get(eaId);
            if (Objects.isNull(player)) {
                continue;
            }
            PlayerValuationEntity valuation = playerValuationRepository
                    .findByBasePlayerIdAndGameVersionIdAndSourceAndOwnerUserIdIsNull(player.getId(), gameVersion, ValuationSource.THIRD_PARTY_SEED)
                    .orElseGet(PlayerValuationEntity::new);
            valuation.setBasePlayerId(player.getId());
            valuation.setGameVersionId(gameVersion);
            valuation.setSource(ValuationSource.THIRD_PARTY_SEED);
            valuation.setModelVersion(MODEL_VERSION);
            Long potential = parseLong(row.get(config.getPotentialColumn()));
            valuation.setPotential(Objects.isNull(potential) ? null : potential.shortValue());
            valuation.setValueEur(parseLong(row.get(config.getValueColumn())));
            valuation.setWageEur(parseLong(row.get(config.getWageColumn())));
            playerValuationRepository.save(valuation);
            written++;
        }
        log.info("Open dataset seed imported rows : {} written : {}", rows.size(), written);
        return written;
    }

    private List<Map<String, String>> readRows(Path path, char delimiter) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (Objects.isNull(headerLine)) {
                throw new ImportParseException("empty file");
            }
            List<String> header = splitLine(headerLine.replace("﻿", ""), delimiter);
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while (Objects.nonNull(line = reader.readLine())) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = splitLine(line, delimiter);
                Map<String, String> row = new java.util.HashMap<>();
                for (int i = 0; i < header.size() && i < cells.size(); i++) {
                    row.put(header.get(i), cells.get(i));
                }
                rows.add(row);
            }
            return rows;
        } catch (IOException e) {
            log.error("Open dataset file unreadable path : {}", path, e);
            throw new ImportParseException("file unreadable");
        }
    }

    public static List<String> splitLine(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == delimiter && !quoted) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString().trim());
        return cells;
    }

    private static Long parseLong(String raw) {
        if (Objects.isNull(raw) || raw.isBlank()) {
            return null;
        }
        try {
            return Math.round(Double.parseDouble(raw.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
