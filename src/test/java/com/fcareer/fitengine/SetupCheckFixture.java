package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.fitengine.service.SetupCheckRules;
import com.fcareer.fitengine.service.SetupCheckService;

/** SetupCheckService'i bağımlılıklarıyla kurar (karakterizasyon testi taşıma öncesi ve sonrası aynı kalsın diye tek yerde). */
final class SetupCheckFixture {

    private SetupCheckFixture() {
    }

    static SetupCheckService service(CareerService careers, PlayerService players) {
        return new SetupCheckService(careers, players, new SetupCheckRules(new ObjectMapper()));
    }
}
