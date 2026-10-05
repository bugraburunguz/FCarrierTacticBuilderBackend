package com.fcareer.roles.controller;

import com.fcareer.roles.model.response.BehaviorTagResponse;
import com.fcareer.roles.model.response.RoleResponse;
import com.fcareer.roles.service.BehaviorTagService;
import com.fcareer.roles.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1)
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final BehaviorTagService behaviorTagService;

    @GetMapping("/roles")
    public List<RoleResponse> roles() {
        return roleService.findAll();
    }

    @GetMapping("/behavior-tags")
    public List<BehaviorTagResponse> behaviorTags(@RequestParam(name = "pos", required = false) String position) {
        return behaviorTagService.findByPosition(position);
    }
}
