package com.digiplus.loganalyzer.config;

import com.digiplus.loganalyzer.detector.DetectorConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Central place for simple bean definitions. */
@Configuration
public class BeansConfig {

    /** Exposes the detector configuration (bound from app.detector.*) as a bean. */
    @Bean
    public DetectorConfig detectorConfig(DetectorProperties properties) {
        return properties.toConfig();
    }
}
