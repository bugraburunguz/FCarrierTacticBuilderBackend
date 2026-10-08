package com.fcareer.versioning.controller;

import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.versioning.model.ChangeType;
import com.fcareer.versioning.model.VersionModels.LatestChanges;
import com.fcareer.versioning.model.VersionModels.VersionInfo;
import com.fcareer.versioning.service.VersioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/versions")
@RequiredArgsConstructor
public class VersioningController {

    private final VersioningService versioningService;
    private final CatalogProperties catalogProperties;

    @GetMapping
    public List<VersionInfo> versions(@RequestParam(name = "gv", required = false) Short gameVersionId) {
        return versioningService.versions(gv(gameVersionId));
    }

    @GetMapping("/latest/changes")
    public LatestChanges latestChanges(@RequestParam(name = "gv", required = false) Short gameVersionId,
                                       @RequestParam(name = "type", required = false) ChangeType type,
                                       @RequestParam(name = "limit", required = false, defaultValue = "0") int limit) {
        return versioningService.latestChanges(gv(gameVersionId), type, limit).orElse(null);
    }

    private short gv(Short requested) {
        return requested == null ? catalogProperties.getDefaultGameVersion() : requested;
    }
}
