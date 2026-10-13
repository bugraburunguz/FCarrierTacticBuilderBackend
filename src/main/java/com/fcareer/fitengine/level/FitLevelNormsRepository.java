package com.fcareer.fitengine.level;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class FitLevelNormsRepository {

    private static final String REAL_ARRAY = "float4";

    private final JdbcTemplate jdbc;

    public record Row(int gender, String position, String roleId, String focus, int ovr, int bandLo, int bandHi, int sampleCount, double[] quantiles) {
    }

    public void deleteByEngineVersion(String engineVersion) {
        jdbc.update("DELETE FROM fit_level_norms WHERE engine_version = ?", engineVersion);
    }

    public void insertAll(String engineVersion, List<Row> rows) {
        jdbc.batchUpdate("INSERT INTO fit_level_norms (gender, position, role_id, focus, ovr, engine_version, band_lo, band_hi, sample_count, quantiles) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                rows, 1000, (ps, row) -> {
                    ps.setInt(1, row.gender());
                    ps.setString(2, row.position());
                    ps.setString(3, row.roleId());
                    ps.setString(4, row.focus());
                    ps.setInt(5, row.ovr());
                    ps.setString(6, engineVersion);
                    ps.setInt(7, row.bandLo());
                    ps.setInt(8, row.bandHi());
                    ps.setInt(9, row.sampleCount());
                    Float[] boxed = new Float[row.quantiles().length];
                    for (int i = 0; i < boxed.length; i++) {
                        boxed[i] = (float) row.quantiles()[i];
                    }
                    Array array = ps.getConnection().createArrayOf(REAL_ARRAY, boxed);
                    ps.setArray(10, array);
                });
    }

    public List<Row> findByEngineVersion(String engineVersion) {
        return jdbc.query("SELECT gender, position, role_id, focus, ovr, band_lo, band_hi, sample_count, quantiles FROM fit_level_norms WHERE engine_version = ?",
                (rs, i) -> {
                    Float[] values = (Float[]) rs.getArray("quantiles").getArray();
                    double[] quantiles = new double[values.length];
                    for (int k = 0; k < values.length; k++) {
                        quantiles[k] = values[k];
                    }
                    return new Row(rs.getInt("gender"), rs.getString("position"), rs.getString("role_id"), rs.getString("focus"), rs.getInt("ovr"),
                            rs.getInt("band_lo"), rs.getInt("band_hi"), rs.getInt("sample_count"), quantiles);
                }, engineVersion);
    }
}
