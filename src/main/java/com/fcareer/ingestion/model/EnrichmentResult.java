package com.fcareer.ingestion.model;

public record EnrichmentResult(int rowsInFile, int playersUpdated, int accelerateRecomputed) {
}
