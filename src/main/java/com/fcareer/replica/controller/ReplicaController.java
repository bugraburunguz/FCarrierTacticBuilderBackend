package com.fcareer.replica.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fcareer.auth.security.AuthUser;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.replica.ReplicaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.fcareer.common.constants.ApiConstants.API_V1;

/** Replika taktikler (E3-9): herkese açık liste ve detay; taktik kodu ve etiketsiz analiz sunucuda üretilir. */
@RestController
@RequestMapping(API_V1 + "/replicas")
@RequiredArgsConstructor
public class ReplicaController {

    private final ReplicaService replicas;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    @GetMapping
    public List<ReplicaService.Replica> list() {
        return replicas.all();
    }

    @GetMapping("/legacy-ids")
    public Map<String, String> legacyIds() {
        return replicas.legacyIds();
    }

    @GetMapping("/{id}")
    public JsonNode detail(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        ReplicaService.Replica replica = replicas.find(id).orElseThrow(() -> new NotFoundException("replica", id));
        ObjectNode out = replicas.rawOf(id).orElseThrow().deepCopy();
        // İç doğrulama notları yalnız yöneticiye; herkese yalnız "doğrulanmadı" bayrağı.
        out.put("verified", replica.verified());
        if (Objects.isNull(user) || !AuthUser.ROLE_ADMIN.equals(user.role())) {
            out.remove("verify");
            out.remove("sources");
        }
        out.put("tacticCode", replica.tacticCode());
        out.set("analysis", mapper.valueToTree(replicas.analyze(replica)));
        return out;
    }
}
