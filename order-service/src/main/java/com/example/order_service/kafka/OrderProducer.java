package com.example.order_service.kafka;


import com.example.order_service.dto.OrderDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OrderProducer {

    private static final String ORDER_PLACED_TOPIC = "order_placed";
    private static final String ORDER_CANCELLED_TOPIC = "order_cancelled";

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Sends an event to the appropriate Kafka topic based on the event type.
     *
     * @param orderDto The details of the order.
     * @param eventType Type of event, either "placed" or "cancelled".
     */
    public void sendOrderEvent(OrderDto orderDto, String eventType) {
        String topic = eventType.equalsIgnoreCase("cancelled") ? ORDER_CANCELLED_TOPIC : ORDER_PLACED_TOPIC;
        Long orderId = orderDto.getId();

        try {
            log.info("=== Kafka Producer: Sending '{}' event for order ID: {} ===", eventType, orderId);
            log.info("Topic: {}", topic);
            log.info("Order Items Count: {}", orderDto.getOrderItems() != null ? orderDto.getOrderItems().size() : 0);

            kafkaTemplate.send(topic, orderDto).whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("✅ Kafka message sent successfully to topic '{}' for order ID: {}", topic, orderId);
                    log.info("Partition: {}, Offset: {}",
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                } else {
                    log.error("❌ Failed to send Kafka message for order ID: {}. Error: {}", orderId, ex.getMessage(), ex);
                }
            });

        } catch (Exception e) {
            log.error("❌ Exception while sending '{}' event for order ID: {}. Error: {}", eventType, orderId, e.getMessage(), e);
        }
    }
}

