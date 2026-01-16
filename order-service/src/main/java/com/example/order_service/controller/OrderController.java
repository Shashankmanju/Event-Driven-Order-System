package com.example.order_service.controller;

import com.example.order_service.client.ProductServiceClient;
import com.example.order_service.dto.OrderDto;
import com.example.order_service.dto.OrderItemDto;
import com.example.order_service.dto.OrderResponse;
import com.example.order_service.dto.ProductAvailability;
import com.example.order_service.kafka.OrderProducer;
import com.example.order_service.service.OrderService;
import jakarta.validation.Valid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;
@Slf4j
@RestController
public class OrderController {

    private final OrderService orderService;
    private final ProductServiceClient productServiceClient;
    private final OrderProducer orderProducer;

    public OrderController(OrderService orderService, ProductServiceClient productServiceClient, OrderProducer orderProducer) {
        this.orderService = orderService;
        this.productServiceClient = productServiceClient;
        this.orderProducer = orderProducer;
    };

    @PostMapping("/create")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderDto orderDto) {
        try {
            // STEP 1: Check product availability
            List<ProductAvailability> availabilityList =
                    productServiceClient.checkProductAvailability(orderDto.getOrderItems());

                    /*
            Receives response from Product Service:
   │ [
   │   { "skuCode": "LAPTOP-001", "available": true }
   │ ]
             */
            // STEP 2: Filter available items
            List<OrderItemDto> availableItems = orderDto.getOrderItems().stream()
                    .filter(item -> orderService.isProductAvailable(item.getSkuCode(), availabilityList))
                    .collect(Collectors.toList());

            if (availableItems.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new OrderResponse("No items available for the order."));
            }

            orderDto.setOrderItems(availableItems);

            // STEP 3: Save order to database
            OrderResponse orderResponse = orderService.createOrder(orderDto);

            // STEP 4: 🔥 KAFKA PRODUCER CALLED HERE!
            if (orderResponse != null) {
                log.info("Order placed successfully, sending 'Order Placed' event to Kafka...");
                orderDto.setId(orderResponse.getOrderId());

                // ✅ THIS IS WHERE KAFKA PRODUCER IS CALLED
                orderProducer.sendOrderEvent(orderDto, "placed");
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(orderResponse);
        } catch (Exception e) {
            log.error("Error while creating order", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to create order");
        }
    }

    @PutMapping("/cancel/{id}")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long id) {
        try {

            OrderResponse orderResponse = orderService.cancelOrder(id);
            return ResponseEntity.ok(orderResponse);
        } catch (Exception e) {
            log.error("Error while cancelling order", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to cancel order");
        }
    }
}
