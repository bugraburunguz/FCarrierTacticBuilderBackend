package com.fcareer.ut.kb;

import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.ingestion.service.CatalogIngestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * V3-05d-b: ut_item temel kartları yerel katalogdan kopyalanır (dış istek yok; `KnowledgeBaseRepository.syncBaseItems`, upsert → idempotent).
 * Kendiliğinden iki durumda: katalog ingestion başarıyla bitince ve açılışta o oyun sürümü için ut_item boşsa. Fiyat gözlemleri ut_item'a bağlı
 * olduğundan boş tablo bütün fiyatların "bilinmeyen kart" diye atlanmasına yol açıyordu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UtItemBaseSync {

    private final KnowledgeBaseRepository repository;
    private final CatalogProperties catalogProperties;

    public int sync(short gameVersion) {
        int upserted = repository.syncBaseItems(gameVersion);
        log.info("ut_item base sync gameVersion : {} upserted : {}", gameVersion, upserted);
        return upserted;
    }

    /** ut_item bu oyun sürümünde boşsa doldurur; doluysa dokunmaz (0). */
    public int syncIfEmpty(short gameVersion) {
        return repository.itemCount(gameVersion) > 0 ? 0 : sync(gameVersion);
    }

    @EventListener
    public void onCatalogIngested(CatalogIngestedEvent event) {
        sync(event.gameVersionId());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        syncIfEmpty(catalogProperties.getDefaultGameVersion());
    }
}
