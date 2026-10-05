package com.fcareer.ingestion.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.ingestion")
public class IngestionProperties {
    private String provider = "mock";
    private short gameVersionId = 25;
    private int pageSize = 100;
    private int maxPlayers = 0;
    private boolean runOnStartup = false;
    private String adminKey = "";
    private DropApi dropApi = new DropApi();
    private Mock mock = new Mock();
    private OpenDataset openDataset = new OpenDataset();

    @Getter
    @Setter
    public static class DropApi {
        private String baseUrl = "https://drop-api.ea.com/rating/ea-sports-fc";
        private String locale = "en";
        private Duration timeout = Duration.ofSeconds(30);
        private Duration pageDelay = Duration.ofMillis(250);
        private String expectedBranding = "FC25";
    }

    @Getter
    @Setter
    public static class Mock {
        private int playerCount = 300;
        private long seed = 27;
    }

    @Getter
    @Setter
    public static class OpenDataset {
        private boolean licenseConfirmed = false;
        private String path = "";
        private String eaIdColumn = "";
        private String potentialColumn = "";
        private String valueColumn = "";
        private String wageColumn = "";
        private char delimiter = ',';
    }
}
