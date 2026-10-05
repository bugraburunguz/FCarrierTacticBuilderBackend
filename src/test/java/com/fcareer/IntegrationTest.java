package com.fcareer;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "zonky.test.database.postgres.initdb.properties.locale=C",
        "fcareer.ingestion.admin-key=test-key",
        "fcareer.ingestion.provider=mock",
        "fcareer.ingestion.game-version-id=25",
        "fcareer.ingestion.mock.player-count=250",
        "fcareer.security.jwt.secret=integration-test-secret-integration-test-secret",
        "fcareer.security.rate-limit.enabled=false"
})
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@AutoConfigureMockMvc
@Import(TestSupportConfiguration.class)
public @interface IntegrationTest {
}
