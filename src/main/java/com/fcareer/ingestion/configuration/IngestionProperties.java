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
    private short gameVersionId = 27;
    private int pageSize = 100;
    private int maxPlayers = 0;
    private boolean runOnStartup = false;
    private String adminKey = "";
    private EaSite eaSite = new EaSite();
    private Mock mock = new Mock();
    private OpenDataset openDataset = new OpenDataset();

    @Getter
    @Setter
    public static class EaSite {
        private String baseUrl = "https://www.ea.com";
        private String locale = "en";
        private String ratingsPath = "/games/ea-sports-fc/ratings";
        private String franchiseSlug = "ea-sports-fc";
        private int sitePageSize = 100;
        private Duration timeout = Duration.ofSeconds(30);
        private Duration pageDelay = Duration.ofMillis(1000);
        private String userAgent = "FCareerIngestion/1.0";
        // Sayfa başlığı bu desene uymuyorsa (ör. site başka bir FC sürümüne geçtiyse) ingestion durur.
        private String expectedTitlePattern = "EA SPORTS FC\\S*\\s+27\\b";
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
