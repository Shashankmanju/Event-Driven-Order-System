package com.example.product_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {
    // @EnableKafka enables detection of @KafkaListener annotations
    // Spring Boot auto-configuration will handle the rest
}

