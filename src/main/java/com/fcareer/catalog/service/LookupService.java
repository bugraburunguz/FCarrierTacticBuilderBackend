package com.fcareer.catalog.service;

import com.fcareer.catalog.model.response.LookupItem;
import com.fcareer.ingestion.base.BaseCatalogImporter;
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
            SELECT l.id, l.name, l.ea_league_id, MIN(c.gender) AS gender, COUNT(DISTINCT c.id) AS clubs
              FROM league l JOIN club c ON c.league_id = l.id
             WHERE (:gender IS NULL OR c.gender = :gender)
               -- ACADEMY-b kararı: yalnız katalog oyuncusu olan kulüpler sayılır; hiç böyle kulübü olmayan lig listelenmez.
               AND EXISTS (SELECT 1 FROM player_rating r JOIN base_player b ON b.id = r.base_player_id WHERE r.club_id = c.id AND b.owner_career_id IS NULL)
             GROUP BY l.id, l.name, l.ea_league_id
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
                .name(BaseCatalogImporter.displayLeagueName(rs.getString("name"), rs.getObject("ea_league_id", Integer.class)))
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
