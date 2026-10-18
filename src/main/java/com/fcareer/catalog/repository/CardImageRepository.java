package com.fcareer.catalog.repository;

import com.fcareer.catalog.service.UpstreamImageCache;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Kart görseli kalıcı önbelleği (V41 card_image): kartlar değişmez; bulunan görsel kalıcı, bulunamayan zamanıyla tutulur. */
@Repository
@RequiredArgsConstructor
public class CardImageRepository implements UpstreamImageCache.Store {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Optional<UpstreamImageCache.Stored> load(long id) {
        return jdbc.query("SELECT content_type, bytes, missing, fetched_at FROM card_image WHERE ea_id = :id", Map.of("id", id), (rs, i) -> {
            Instant fetchedAt = rs.getTimestamp("fetched_at").toInstant();
            UpstreamImageCache.Image image = rs.getBoolean("missing") ? null : new UpstreamImageCache.Image(rs.getBytes("bytes"), rs.getString("content_type"));
            return new UpstreamImageCache.Stored(image, fetchedAt);
        }).stream().findFirst();
    }

    @Override
    public void save(long id, UpstreamImageCache.Stored stored) {
        UpstreamImageCache.Image image = stored.image();
        jdbc.update("""
                INSERT INTO card_image (ea_id, content_type, bytes, missing, fetched_at) VALUES (:id, :type, :bytes, :missing, :at)
                ON CONFLICT (ea_id) DO UPDATE SET content_type = EXCLUDED.content_type, bytes = EXCLUDED.bytes, missing = EXCLUDED.missing,
                                                  fetched_at = EXCLUDED.fetched_at""",
                new MapSqlParameterSource().addValue("id", id)
                        .addValue("type", Objects.isNull(image) ? null : image.contentType())
                        .addValue("bytes", Objects.isNull(image) ? null : image.bytes())
                        .addValue("missing", Objects.isNull(image))
                        .addValue("at", Timestamp.from(stored.fetchedAt())));
    }
}
