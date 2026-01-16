package com.example.product_service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.product_service.dto.OrderItemDto;
import com.example.product_service.dto.OrderMessage;
import com.example.product_service.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ProductConsumer {

    @Autowired
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String ORDER_PLACED_TOPIC = "order_placed";
    private static final String ORDER_CANCELLED_TOPIC = "order_cancelled";

    @KafkaListener(topics = ORDER_PLACED_TOPIC)
    public void consumeOrderPlaced(String message) {

        log.info("=== Kafka Consumer: Received ORDER_PLACED event ===");
        log.info("Raw message: {}", message);

        try {
            OrderMessage order =
                    objectMapper.readValue(message, OrderMessage.class);

            log.info("Parsed order successfully. Order Items: {}",
                order.getOrderItems() != null ? order.getOrderItems().size() : 0);

            List<OrderItemDto> items = order.getOrderItems();

            if (items != null && !items.isEmpty()) {
                log.info("Processing {} items for inventory reduction", items.size());
                items.forEach(item -> {
                    log.info("Reducing inventory - SKU: {}, Quantity: {}",
                        item.getSkuCode(), item.getQuantity());
                    try {
                        productService.reduceProductQuantity(
                                item.getSkuCode(),
                                item.getQuantity()
                        );
                        log.info("✅ Successfully reduced inventory for SKU: {}", item.getSkuCode());
                    } catch (Exception e) {
                        log.error("❌ Failed to reduce inventory for SKU: {}. Error: {}",
                            item.getSkuCode(), e.getMessage(), e);
                    }
                });
                log.info("=== ORDER_PLACED event processing completed ===");
            } else {
                log.warn("⚠️  No order items found in the message!");
            }

        } catch (JsonProcessingException e) {
            log.error("❌ Error parsing ORDER_PLACED event: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Unexpected error processing ORDER_PLACED event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = ORDER_CANCELLED_TOPIC)
    public void consumeOrderCancelled(String message) {

        log.info("=== Kafka Consumer: Received ORDER_CANCELLED event ===");
        log.info("Raw message: {}", message);

        try {
            OrderMessage order =
                    objectMapper.readValue(message, OrderMessage.class);

            log.info("Parsed order successfully. Order Items: {}",
                order.getOrderItems() != null ? order.getOrderItems().size() : 0);

            List<OrderItemDto> items = order.getOrderItems();

            if (items != null && !items.isEmpty()) {
                log.info("Processing {} items for inventory restoration", items.size());
                items.forEach(item -> {
                    log.info("Restoring inventory - SKU: {}, Quantity: {}",
                        item.getSkuCode(), item.getQuantity());
                    try {
                        productService.increaseProductQuantity(
                                item.getSkuCode(),
                                item.getQuantity()
                        );
                        log.info("✅ Successfully restored inventory for SKU: {}", item.getSkuCode());
                    } catch (Exception e) {
                        log.error("❌ Failed to restore inventory for SKU: {}. Error: {}",
                            item.getSkuCode(), e.getMessage(), e);
                    }
                });
                log.info("=== ORDER_CANCELLED event processing completed ===");
            } else {
                log.warn("⚠️  No order items found in the message!");
            }

        } catch (JsonProcessingException e) {
            log.error("❌ Error parsing ORDER_CANCELLED event: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Unexpected error processing ORDER_CANCELLED event: {}", e.getMessage(), e);
        }
    }
}