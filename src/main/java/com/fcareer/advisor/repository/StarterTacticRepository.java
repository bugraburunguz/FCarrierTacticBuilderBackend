package com.fcareer.advisor.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

/** Başlangıç önerisi önbellek anahtarı: kariyerin son içe aktarma anlık görüntüsü ve son kadro hamlesi (alım/satış kadroyu içe aktarmasız da değiştirir). */
@Repository
@RequiredArgsConstructor
public class StarterTacticRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public String squadVersion(long careerId) {
        return jdbc.queryForObject("""
                SELECT COALESCE((SELECT max(id) FROM career_snapshot WHERE career_id = :c AND source = 'IMPORT'), 0) || ':'
                    || COALESCE((SELECT max(id) FROM roster_event WHERE career_id = :c), 0)""", Map.of("c", careerId), String.class);
    }
}
