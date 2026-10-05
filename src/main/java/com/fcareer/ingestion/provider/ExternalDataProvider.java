package com.fcareer.ingestion.provider;

import com.fcareer.ingestion.model.ExternalPlayerPage;

public interface ExternalDataProvider {

    String name();

    ExternalPlayerPage fetch(int offset, int limit);

    default long pageDelayMillis() {
        return 0;
    }
}
