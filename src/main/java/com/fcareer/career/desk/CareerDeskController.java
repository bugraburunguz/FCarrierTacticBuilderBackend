package com.fcareer.career.desk;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.career.desk.CareerDeskModels.Backup;
import com.fcareer.career.desk.CareerDeskModels.DiffResult;
import com.fcareer.career.desk.CareerDeskModels.HistoryPoint;
import com.fcareer.career.desk.CareerDeskModels.OvrBatch;
import com.fcareer.career.desk.CareerDeskModels.SeasonRecordDto;
import com.fcareer.career.desk.CareerDeskModels.SeasonRecordRequest;
import com.fcareer.career.desk.CareerDeskModels.ShortlistViewDto;
import com.fcareer.career.desk.CareerDeskModels.ShortlistRequest;
import com.fcareer.career.desk.CareerDeskModels.SnapshotDto;
import com.fcareer.career.desk.CareerDeskModels.TagDto;
import com.fcareer.career.desk.CareerDeskModels.TagRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/careers/{careerId}")
@RequiredArgsConstructor
public class CareerDeskController {

    private final CareerDeskService service;

    @GetMapping("/snapshots")
    public List<SnapshotDto> snapshots(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId) {
        return service.snapshots(user.id(), careerId);
    }

    @GetMapping("/snapshots/diff")
    public DiffResult diff(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId,
                           @RequestParam(required = false) Long from, @RequestParam(required = false) Long to) {
        return service.diff(user.id(), careerId, from, to);
    }

    @GetMapping("/players/{playerId}/history")
    public List<HistoryPoint> history(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId, @PathVariable Long playerId) {
        return service.history(user.id(), careerId, playerId);
    }

    @PutMapping("/players/ratings")
    public SnapshotDto updateRatings(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId, @Valid @RequestBody OvrBatch batch) {
        return service.updateRatings(user.id(), careerId, batch);
    }

    @GetMapping("/tags")
    public List<TagDto> tags(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId) {
        return service.tags(user.id(), careerId);
    }

    @PutMapping("/tags/{playerId}")
    public TagDto setTag(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId, @PathVariable Long playerId, @Valid @RequestBody TagRequest request) {
        return service.setTag(user.id(), careerId, playerId, request.tag(), request.askingPriceEur());
    }

    @DeleteMapping("/tags/{playerId}")
    public void clearTag(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId, @PathVariable Long playerId) {
        service.clearTag(user.id(), careerId, playerId);
    }

    @GetMapping("/shortlist")
    public List<ShortlistViewDto> shortlist(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId) {
        return service.shortlistView(user.id(), careerId);
    }

    @PutMapping("/shortlist/{playerId}")
    public void shortlistAdd(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId, @PathVariable Long playerId, @Valid @RequestBody ShortlistRequest request) {
        service.shortlistAdd(user.id(), careerId, playerId, request);
    }

    @DeleteMapping("/shortlist/{playerId}")
    public void shortlistRemove(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId, @PathVariable Long playerId) {
        service.shortlistRemove(user.id(), careerId, playerId);
    }

    @GetMapping("/seasons")
    public List<SeasonRecordDto> seasons(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId) {
        return service.seasons(user.id(), careerId);
    }

    @PutMapping("/seasons/{season}")
    public void putSeason(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId, @PathVariable int season, @Valid @RequestBody SeasonRecordRequest request) {
        service.putSeason(user.id(), careerId, season, request);
    }

    @GetMapping("/backup")
    public Backup backup(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId) {
        return service.backup(user.id(), careerId);
    }

    @PostMapping("/backup")
    public Backup restore(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId, @Valid @RequestBody Backup backup) {
        return service.restore(user.id(), careerId, backup);
    }
}
