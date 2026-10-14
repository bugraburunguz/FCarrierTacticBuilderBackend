package com.fcareer.replica.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.replica.ReplicaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping("/{id}")
    public JsonNode detail(@PathVariable String id) {
        ReplicaService.Replica replica = replicas.find(id).orElseThrow(() -> new NotFoundException("replica", id));
        ObjectNode out = replicas.rawOf(id).orElseThrow().deepCopy();
        out.put("tacticCode", replica.tacticCode());
        out.set("analysis", mapper.valueToTree(replicas.analyze(replica)));
        return out;
    }
}
