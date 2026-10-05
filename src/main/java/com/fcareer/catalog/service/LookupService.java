package com.fcareer.catalog.service;

import com.fcareer.catalog.model.response.LookupItem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LookupService {

    private static final String LEAGUES = """
            SELECT l.id, l.name, MIN(c.gender) AS gender, COUNT(DISTINCT c.id) AS clubs
              FROM league l JOIN club c ON c.league_id = l.id
             WHERE (:gender IS NULL OR c.gender = :gender)
             GROUP BY l.id, l.name
             ORDER BY l.name
            """;
    private static final String NATIONALITIES = """
            SELECT n.id, n.name, COUNT(*) AS players
              FROM nationality n JOIN base_player b ON b.nationality_id = n.id
             GROUP BY n.id, n.name
             ORDER BY n.name
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public List<LookupItem> leagues(Integer gender) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("gender", gender, java.sql.Types.INTEGER);
        return jdbc.query(LEAGUES, params, (rs, i) -> LookupItem.builder()
                .id(rs.getInt("id"))
                .name(rs.getString("name"))
                .gender(Objects.isNull(rs.getObject("gender")) ? null : rs.getInt("gender"))
                .count(rs.getLong("clubs"))
                .build());
    }

    public List<LookupItem> nationalities() {
        return jdbc.query(NATIONALITIES, (rs, i) -> LookupItem.builder()
                .id(rs.getInt("id"))
                .name(rs.getString("name"))
                .count(rs.getLong("players"))
                .build());
    }
}
