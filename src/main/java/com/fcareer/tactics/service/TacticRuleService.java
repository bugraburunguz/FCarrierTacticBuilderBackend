package com.fcareer.tactics.service;

import com.fcareer.fitengine.model.TacticRuleDefinition;
import com.fcareer.tactics.mapper.TacticMapper;
import com.fcareer.tactics.repository.TacticRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TacticRuleService {

    private final TacticRuleRepository tacticRuleRepository;
    private final TacticMapper tacticMapper;

    public List<TacticRuleDefinition> enabledRules() {
        return tacticRuleRepository.findByEnabledTrue().stream().map(tacticMapper::toRuleDefinition).toList();
    }
}
