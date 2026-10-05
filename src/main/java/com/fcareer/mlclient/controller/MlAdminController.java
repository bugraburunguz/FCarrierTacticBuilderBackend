package com.fcareer.mlclient.controller;

import com.fcareer.mlclient.service.MlValuationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.ADMIN_PATH;

@RestController
@RequestMapping(ADMIN_PATH + "/ml")
@RequiredArgsConstructor
public class MlAdminController {

    private final MlValuationService mlValuationService;

    @PostMapping("/valuations")
    public int generate(@RequestParam(name = "gv", required = false) Short gameVersionId) {
        return mlValuationService.run(gameVersionId);
    }
}
