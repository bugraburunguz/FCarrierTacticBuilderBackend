package com.fcareer.ut.controller;

import com.fcareer.ut.model.UtCaptureModels.CaptureRequest;
import com.fcareer.ut.model.UtCaptureModels.CaptureResult;
import com.fcareer.ut.service.UtCaptureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/ut")
@RequiredArgsConstructor
public class UtCaptureController {

    private final UtCaptureService utCaptureService;

    @PostMapping("/capture")
    public CaptureResult capture(@org.springframework.security.core.annotation.AuthenticationPrincipal com.fcareer.auth.security.AuthUser user, @Valid @RequestBody CaptureRequest request) {
        return utCaptureService.process(request.captures(), user.id());
    }
}
