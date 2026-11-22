package com.fcareer.catalog.squad;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/squad-versions")
@RequiredArgsConstructor
public class SquadVersionController {

    private final SquadVersionService squadVersionService;

    @GetMapping
    public List<SquadVersionResponse> list() {
        return squadVersionService.list();
    }
}
