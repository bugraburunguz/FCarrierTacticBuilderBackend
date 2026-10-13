package com.fcareer.catalog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
@RequiredArgsConstructor
public class LeagueTierStartupRunner implements ApplicationRunner {

    private final LeagueTierService leagueTierService;

    @Override
    public void run(ApplicationArguments args) {
        leagueTierService.apply();
    }
}
