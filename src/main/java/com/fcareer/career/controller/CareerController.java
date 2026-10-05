package com.fcareer.career.controller;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.career.model.request.CreateCareerRequest;
import com.fcareer.career.model.request.RosterEventRequest;
import com.fcareer.career.model.response.CareerPlayerResponse;
import com.fcareer.career.model.response.CareerResponse;
import com.fcareer.career.model.response.RosterEventResponse;
import com.fcareer.career.service.CareerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/careers")
@RequiredArgsConstructor
public class CareerController {

    private final CareerService careerService;

    @PostMapping
    public CareerResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateCareerRequest request) {
        return careerService.create(user.id(), request);
    }

    @GetMapping
    public List<CareerResponse> list(@AuthenticationPrincipal AuthUser user) {
        return careerService.list(user.id());
    }

    @GetMapping("/{id}")
    public CareerResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return careerService.get(user.id(), id);
    }

    @GetMapping("/{id}/squad")
    public List<CareerPlayerResponse> squad(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return careerService.squad(user.id(), id);
    }

    @PostMapping("/{id}/events")
    public RosterEventResponse applyEvent(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                          @Valid @RequestBody RosterEventRequest request) {
        return careerService.applyEvent(user.id(), id, request);
    }

    @GetMapping("/{id}/events")
    public List<RosterEventResponse> events(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return careerService.events(user.id(), id);
    }
}
