package com.fcareer.common.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

import static com.fcareer.common.constants.ApiConstants.CATALOG_HTTP_MAX_AGE;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CatalogResponses {

    public static <T> ResponseEntity<T> toPublicCached(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(CATALOG_HTTP_MAX_AGE).cachePublic()).body(body);
    }
}
