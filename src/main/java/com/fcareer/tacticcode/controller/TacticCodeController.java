package com.fcareer.tacticcode.controller;

import com.fcareer.tacticcode.model.TacticCodeModels.ExportResponse;
import com.fcareer.tacticcode.model.TacticCodeModels.ImportRequest;
import com.fcareer.tacticcode.model.TacticCodeModels.ImportResponse;
import com.fcareer.tacticcode.service.TacticCodeService;
import com.fcareer.tactics.model.request.TacticRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/tactics")
@RequiredArgsConstructor
public class TacticCodeController {

    private final TacticCodeService tacticCodeService;

    @PostMapping("/export-code")
    public ExportResponse export(@Valid @RequestBody TacticRequest request) {
        return tacticCodeService.export(request);
    }

    @PostMapping("/import-code")
    public ImportResponse importCode(@Valid @RequestBody ImportRequest request) {
        return tacticCodeService.importCode(request.getCode());
    }
}
