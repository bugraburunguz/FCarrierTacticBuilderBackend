package com.fcareer.ingestion.model;

import java.util.List;

public record ExternalPlayerPage(List<ExternalPlayer> items, int totalItems) {
}
