package com.fcareer.tacticanalysis;

import com.fcareer.tacticanalysis.TacticAnalysisModels.Analysis;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Input;
import org.springframework.stereotype.Component;

/** Taktik analizi (13 §3.4): yerleştir → 7 metrik → kurallar. Saf fonksiyon: aynı girdi aynı bulguları verir. */
@Component
public class TacticAnalyzer {

    private final RoleMovements movements;
    private final TacticRules rules;
    private final MetricsCalculator calculator;
    private final RuleEngine engine;

    public TacticAnalyzer(RoleMovements movements, TacticRules rules, MetricsCalculator calculator, RuleEngine engine) {
        this.movements = movements;
        this.rules = rules;
        this.calculator = calculator;
        this.engine = engine;
    }

    public Analysis analyze(Input input) {
        TacticContext ctx = TacticContext.of(input, movements, rules.params());
        RuleEngine.Result result = engine.evaluate(ctx);
        return new Analysis(calculator.calculate(ctx), result.findings(), result.notEvaluated(), input.levelLabel());
    }
}
