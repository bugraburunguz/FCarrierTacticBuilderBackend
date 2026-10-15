package com.fcareer.ingestion.service;

/** V3-05d-b: katalog ingestion koşusu başarıyla bitti (dinleyiciler: ut_item temel senkronu). */
public record CatalogIngestedEvent(short gameVersionId, long runId) {
}
