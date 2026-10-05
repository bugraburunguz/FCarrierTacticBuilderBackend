package com.fcareer.ingestion.model;

public record IngestionResult(String provider, short gameVersionId, int created, int updated, boolean skipped) {

    public static IngestionResult alreadyRunning(String provider) {
        return new IngestionResult(provider, (short) 0, 0, 0, true);
    }
}
