package com.library.notification.config;

import com.library.common.idcard.IdCardPdfGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdCardConfig {

    @Bean
    public IdCardPdfGenerator idCardPdfGenerator() {
        return new IdCardPdfGenerator();
    }
}
