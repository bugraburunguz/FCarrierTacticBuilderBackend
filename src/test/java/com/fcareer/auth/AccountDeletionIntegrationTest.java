package com.fcareer.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static com.fcareer.CareerTestSupport.FIRST_MOCK_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * PROD-PREP 4 (KVKK): "Hesabımı sil". Mevcut şifre + e-posta yazma onayı; kullanıcı, kariyerleri ve tüm kariyer verisi, oylar, oturumlar,
 * katkıcı kaydı silinir; bilgi tabanı katkıları kalır ama hesapla bağı kopar. Kalıntı kontrolü tablo listesini şemadan üretir
 * (app_user ya da career'a FK veren her sütun + adı user_id/owner_user_id olan her sütun), yeni tablo eklenince test kendiliğinden kapsar.
 */
@IntegrationTest
class AccountDeletionIntegrationTest {

    private static final String PASSWORD = "S3cure-pass!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private CareerTestSupport support;

    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, Object body, String token, int expectedStatus) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(objectMapper.writeValueAsBytes(body));
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        var response = mockMvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expectedStatus);
        return response.getContentAsString().isEmpty() ? objectMapper.nullNode() : objectMapper.readTree(response.getContentAsString());
    }

    private record Account(String token, long userId, String email, long careerId, String refreshToken) {
    }

    /** Kariyer (içe aktarılmış oyuncularla), etiket, kısa liste, sezon kaydı, SBC oyu, katkıcı + katkılar, EA kimlik oyu: her şey dolu bir hesap. */
    private Account populatedAccount() throws Exception {
        String token = support.register();
        JsonNode me = call(get("/api/v1/me"), null, token, 200).at("/data");
        long userId = me.at("/id").asLong();
        String email = me.at("/email").asText();
        String refresh = call(post("/api/v1/auth/login"), Map.of("email", email, "password", PASSWORD), null, 200).at("/data/refreshToken").asText();
        long careerId = support.premiumCareer(token, 10_000_000L);
        support.importCsv(careerId, token, FIRST_MOCK_ID + ",80,85\n", FIRST_MOCK_ID + ",1\n", null);
        jdbc.update("INSERT INTO career_player_tag (career_id, base_player_id, tag) VALUES (?, ?, 'LOCKED')", careerId, FIRST_MOCK_ID);
        jdbc.update("INSERT INTO career_shortlist (career_id, base_player_id) VALUES (?, ?)", careerId, FIRST_MOCK_ID + 1);
        jdbc.update("INSERT INTO career_season_record (career_id, season) VALUES (?, 2027)", careerId);
        jdbc.update("INSERT INTO sbc_vote (user_id, set_id, vote) VALUES (?, ?, 1)", userId, 990_000 + userId);
        jdbc.update("INSERT INTO user_tactic (user_id, slot, name, formation) VALUES (?, 1, 'T', '4-3-3')", userId);
        long contributorId = jdbc.queryForObject("INSERT INTO contributor (user_id) VALUES (?) RETURNING id", Long.class, userId);
        jdbc.update("INSERT INTO contributor_audit (user_id, action, actor) VALUES (?, 'GRANTED', 'test')", userId);
        jdbc.update("INSERT INTO contrib_event (contributor_id, kind, natural_key, content_hash, payload) VALUES (?, 'SBC', ?, 'h', '{}'::jsonb)", contributorId, "del-" + userId);
        jdbc.update("INSERT INTO price_obs (ut_item_id, platform, observed_at, kind, value, contributor_id) VALUES (?, 'CONSOLE', now(), 'BIN', 1000, ?)", 880_000 + userId, contributorId);
        jdbc.update("INSERT INTO ea_id_suggestion (kind, ea_id, name, votes, voters) VALUES ('LEAGUE', ?, 'Del', 1, ARRAY[?]::bigint[])", (int) (770_000 + userId), userId);
        return new Account(token, userId, email, careerId, refresh);
    }

    private void delete(Account account, String password, String emailConfirm, int expectedStatus) throws Exception {
        call(post("/api/v1/me/delete"), Map.of("password", password, "emailConfirm", emailConfirm), account.token(), expectedStatus);
    }

    /** Şemadan: app_user'a FK veren ya da adı user_id/owner_user_id olan sütunlar (app_user'ın kendisi hariç). */
    private List<String[]> userColumns() {
        return jdbc.query("""
                SELECT DISTINCT c.table_name, c.column_name
                FROM information_schema.columns c
                JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name AND t.table_type = 'BASE TABLE'
                WHERE c.table_schema = 'public' AND (c.column_name IN ('user_id', 'owner_user_id')
                  OR (c.table_name, c.column_name) IN (
                    SELECT kcu.table_name, kcu.column_name FROM information_schema.referential_constraints rc
                    JOIN information_schema.key_column_usage kcu ON kcu.constraint_name = rc.constraint_name
                    JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = rc.unique_constraint_name
                    WHERE ccu.table_name = 'app_user'))
                  AND c.table_name <> 'app_user'
                ORDER BY 1, 2""", (rs, i) -> new String[] {rs.getString(1), rs.getString(2)});
    }

    /** Şemadan: career'a FK veren sütunlar (kariyer verisi tabloları). */
    private List<String[]> careerColumns() {
        return jdbc.query("""
                SELECT DISTINCT kcu.table_name, kcu.column_name FROM information_schema.referential_constraints rc
                JOIN information_schema.key_column_usage kcu ON kcu.constraint_name = rc.constraint_name
                JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = rc.unique_constraint_name
                WHERE ccu.table_name = 'career' ORDER BY 1, 2""", (rs, i) -> new String[] {rs.getString(1), rs.getString(2)});
    }

    @Test
    void wrongPasswordOrEmailIsRejectedAndNothingIsDeleted() throws Exception {
        Account account = populatedAccount();
        delete(account, "yanlis-sifre", account.email(), 422);
        delete(account, PASSWORD, "baska@example.com", 422);
        call(post("/api/v1/me/delete"), Map.of("password", PASSWORD, "emailConfirm", account.email()), null, 401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE id = ?", Integer.class, account.userId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM career WHERE id = ?", Integer.class, account.careerId())).isEqualTo(1);
    }

    @Test
    void deletionLeavesNoTraceInAnyUserOrCareerTableAndAnonymizesContributions() throws Exception {
        Account account = populatedAccount();
        List<String[]> userColumns = userColumns();
        List<String[]> careerColumns = careerColumns();
        assertThat(userColumns.stream().map(c -> c[0])).contains("career", "sbc_vote", "contributor", "contributor_audit", "credit_transaction", "user_tactic", "player_valuation");
        assertThat(careerColumns.stream().map(c -> c[0])).contains("career_player", "career_player_tag", "career_shortlist", "career_snapshot", "user_save_snapshot", "base_player");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM career_player WHERE career_id = ?", Integer.class, account.careerId())).isPositive();

        delete(account, PASSWORD, "  " + account.email().toUpperCase() + " ", 200);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE id = ?", Integer.class, account.userId())).isZero();
        for (String[] column : userColumns) {
            Integer left = jdbc.queryForObject("SELECT count(*) FROM " + column[0] + " WHERE " + column[1] + " = ?", Integer.class, account.userId());
            assertThat(left).as(column[0] + "." + column[1]).isZero();
        }
        for (String[] column : careerColumns) {
            Integer left = jdbc.queryForObject("SELECT count(*) FROM " + column[0] + " WHERE " + column[1] + " = ?", Integer.class, account.careerId());
            assertThat(left).as(column[0] + "." + column[1]).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ea_id_suggestion WHERE ? = ANY(voters)", Integer.class, account.userId())).isZero();

        // Katkılar kalır, hesapla bağı (katkıcı kaydı) kalkar.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contrib_event WHERE natural_key = ? AND contributor_id IS NULL", Integer.class, "del-" + account.userId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM price_obs WHERE ut_item_id = ? AND contributor_id IS NULL", Integer.class, 880_000 + account.userId())).isEqualTo(1);

        // Oturumlar kapandı; açık erişim anahtarı (süresi dolmamış JWT) da reddedilir (DEPLOY-01 0c).
        call(get("/api/v1/me"), null, account.token(), 401);
        call(get("/api/v1/careers"), null, account.token(), 401);
        call(post("/api/v1/auth/refresh"), Map.of("refreshToken", account.refreshToken()), null, 401);
        call(post("/api/v1/auth/login"), Map.of("email", account.email(), "password", PASSWORD), null, 401);
    }

    @Test
    void theLastAdminCannotDeleteTheirAccount() throws Exception {
        Account account = populatedAccount();
        List<Long> admins = jdbc.queryForList("SELECT id FROM app_user WHERE role = 'ADMIN'", Long.class);
        try {
            jdbc.update("UPDATE app_user SET role = 'USER' WHERE role = 'ADMIN'");
            jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE id = ?", account.userId());
            delete(account, PASSWORD, account.email(), 409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE id = ?", Integer.class, account.userId())).isEqualTo(1);
        } finally {
            jdbc.update("UPDATE app_user SET role = 'USER' WHERE id = ?", account.userId());
            admins.forEach(id -> jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE id = ?", id));
        }
    }
}
