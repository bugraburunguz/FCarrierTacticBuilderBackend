package com.fcareer.catalog.controller;

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

/** Ham görsel döner (ApiResponse zarfı yok): tarayıcı <img> ile doğrudan okur; sonuçlar 30 gün önbelleklenir. */
@RestController
@RequestMapping(API_V1 + "/img")
@RequiredArgsConstructor
public class FaceImageController {

    private static final Duration MISSING_TTL = Duration.ofHours(1);
    private static final Duration FOUND_TTL = Duration.ofDays(30);

    private final FaceImageService faceImageService;

    @GetMapping("/face/{id:\\d+}")
    public ResponseEntity<byte[]> face(@PathVariable long id) {
        return faceImageService.face(id)
                .map(bytes -> ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.maxAge(FOUND_TTL).cachePublic().immutable()).body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().cacheControl(CacheControl.maxAge(MISSING_TTL).cachePublic()).build());
    }
}
