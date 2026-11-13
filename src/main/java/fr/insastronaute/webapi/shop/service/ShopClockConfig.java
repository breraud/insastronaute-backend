package fr.insastronaute.webapi.shop.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class ShopClockConfig {
    @Bean
    Clock shopClock() {
        return Clock.systemUTC();
    }
}
