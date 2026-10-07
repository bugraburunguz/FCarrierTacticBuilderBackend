package com.fcareer.gameconfig;

import com.fcareer.gameconfig.GameConfigService.KeyAttributes;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/config")
@RequiredArgsConstructor
public class GameConfigController {

    private final GameConfigService gameConfigService;

    @GetMapping("/key-attributes")
    public KeyAttributes keyAttributes() {
        return gameConfigService.keyAttributes();
    }
}
