package com.fcareer.catalog.controller;

import com.fcareer.catalog.service.CardImageService;
import com.fcareer.catalog.service.FaceImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

import static com.fcareer.common.constants.ApiConstants.API_V1;

/** Ham görsel döner (ApiResponse zarfı yok): tarayıcı <img> ile doğrudan okur; tarayıcı önbelleği 24 saat (immutable değil: kaldırma talebi 48 saatte yansımalı, BE-SEC-11). */
@RestController
@RequestMapping(API_V1 + "/img")
@RequiredArgsConstructor
public class FaceImageController {

    private static final Duration MISSING_TTL = Duration.ofHours(1);
    private static final Duration FOUND_TTL = Duration.ofHours(24);

    private final FaceImageService faceImageService;
    private final CardImageService cardImageService;

    /** 1×1 saydam GIF: kartı olmayan oyuncu için (404 tarayıcı konsoluna her kart için hata düşürüyordu). */
    private static final byte[] TRANSPARENT_PIXEL = java.util.Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");

    /**
     * Oyuncu kartı görseli (EA reyting görseli, sunucu üzerinden). Yoksa 200 + 1×1 saydam GIF ve `X-Card-Image: missing`: web görselin doğal
     * genişliği 1 px ise kendi kart tasarımını gösterir.
     */
    @GetMapping("/card/{id:\\d+}")
    public ResponseEntity<byte[]> card(@PathVariable long id) {
        return cardImageService.card(id)
                .map(image -> ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).cacheControl(CacheControl.maxAge(FOUND_TTL).cachePublic()).body(image.bytes()))
                .orElseGet(() -> ResponseEntity.ok().contentType(MediaType.IMAGE_GIF).header("X-Card-Image", "missing")
                        .cacheControl(CacheControl.maxAge(FOUND_TTL).cachePublic()).body(TRANSPARENT_PIXEL));
    }

    @GetMapping("/face/{id:\\d+}")
    public ResponseEntity<byte[]> face(@PathVariable long id) {
        return faceImageService.face(id)
                .map(image -> ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).cacheControl(CacheControl.maxAge(FOUND_TTL).cachePublic()).body(image.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().cacheControl(CacheControl.maxAge(MISSING_TTL).cachePublic()).build());
    }
}
