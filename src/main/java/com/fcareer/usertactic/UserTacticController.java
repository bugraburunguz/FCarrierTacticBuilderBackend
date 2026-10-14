package com.fcareer.usertactic;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.usertactic.model.UserTacticModels.SaveRequest;
import com.fcareer.usertactic.model.UserTacticModels.UserTactic;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/user-tactics")
@RequiredArgsConstructor
public class UserTacticController {

    private final UserTacticService service;

    @GetMapping
    public List<UserTactic> list(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) Long careerId) {
        return service.list(user.id(), careerId);
    }

    @PutMapping("/{slot}")
    public UserTactic save(@AuthenticationPrincipal AuthUser user, @PathVariable int slot, @Valid @RequestBody SaveRequest request) {
        return service.save(user.id(), slot, request);
    }

    @DeleteMapping("/{slot}")
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable int slot, @RequestParam(required = false) Long careerId) {
        service.delete(user.id(), careerId, slot);
    }
}
