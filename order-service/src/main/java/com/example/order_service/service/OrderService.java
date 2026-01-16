package com.example.order_service.service;

import com.example.order_service.dto.OrderDto;
import com.example.order_service.dto.OrderResponse;
import com.example.order_service.dto.ProductAvailability;
import com.example.order_service.dto.OrderItemDto;
import com.example.order_service.entity.Order;
import com.example.order_service.entity.OrderItem;
import com.example.order_service.entity.OrderStatus;
import com.example.order_service.kafka.OrderProducer;
import com.example.order_service.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderProducer orderProducer;

    @Autowired
    private MeterRegistry meterRegistry;

    @Transactional
    public OrderResponse createOrder(OrderDto orderDto) {

        Timer.Sample sample = Timer.start(meterRegistry);

        // Convert incoming OrderDto (request) into Order entity (DB object)
        Order order = convertToEntity(orderDto);

        // ✅ Order placed counter
        meterRegistry.counter("orders.placed.count").increment();

        // ✅ Order creation time
        sample.stop(meterRegistry.timer("orders.creation.time"));

        // Create a final reference so it can be used inside lambda expression
        Order finalOrder = order;

        // Convert each OrderItemDto into OrderItem entity
        List<OrderItem> orderItemList = orderDto.getOrderItems().stream()
                .map(itemDto -> {

                    // Create OrderItem entity
                    OrderItem orderItem = new OrderItem();

                    // Set product SKU (used as product identifier)
                    orderItem.setProductId(itemDto.getSkuCode());

                    // Set product name
                    orderItem.setProductName(itemDto.getProductName());

                    // Set quantity ordered
                    orderItem.setQuantity(itemDto.getQuantity());

                    // Set price per unit
                    orderItem.setPrice(itemDto.getPrice());

                    // Set parent Order reference (Many-to-One relationship)
                    orderItem.setOrder(finalOrder);

                    return orderItem;
                })
                .collect(Collectors.toList());

        // Attach all order items to the order (One-to-Many relationship)
        order.setOrderItems(orderItemList);

        // Set initial order status
        order.setOrderStatus(OrderStatus.ORDER_PLACED.name());

        // Persist Order and OrderItems in DB
        // Because of cascading, OrderItems are saved automatically
        order = orderRepository.save(order);

        // Build and return response object
        return OrderResponse.builder()
                .orderId(order.getId())
                .status("ORDER PLACED")
                .build();
    }


    @Transactional
    public OrderDto updateOrder(Long id, OrderDto orderDto) {

        // 1️⃣ Fetch existing Order entity from DB using order ID
        // If not found, throw exception
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // 2️⃣ Update simple fields of Order entity
        order.setTotalPrice(orderDto.getTotalPrice());
        order.setOrderDate(orderDto.getOrderDate());

        // 3️⃣ Remove all existing OrderItems from the order
        // This clears the current child entities
        order.getOrderItems().clear();

        // 4️⃣ Convert incoming OrderItem DTOs into OrderItem entities
        // and attach them to the Order
        order.getOrderItems().addAll(
                orderDto.getOrderItems().stream()
                        .map(this::convertToEntity) // DTO → Entity
                        .collect(Collectors.toList())
        );

        // 5️⃣ Save updated Order (and OrderItems due to cascade)
        order = orderRepository.save(order);

        // ✅ Order cancelled counter
        meterRegistry.counter("orders.cancelled.count").increment();

        // 6️⃣ Convert updated Order entity back to DTO and return
        return convertToDto(order);
    }

    public void deleteOrder(Long id) {
        orderRepository.deleteById(id);
    }

    public OrderItemDto convertToDto(OrderItem orderItem) {
        OrderItemDto itemDto = new OrderItemDto();
        itemDto.setSkuCode(orderItem.getProductId());
        itemDto.setProductName(orderItem.getProductName());
        itemDto.setQuantity(orderItem.getQuantity());
        return itemDto;
    }

   /* public OrderItemDto convertToDto(OrderItem orderItem) {
        OrderItemDto itemDto = new OrderItemDto();   // ✅ correct type
        itemDto.setSkuCode(orderItem.getProductId());
        itemDto.setProductName(orderItem.getProductName());
        itemDto.setQuantity(orderItem.getQuantity());
        itemDto.setPrice(orderItem.getPrice());

        return itemDto; // ✅ must return
    }*/


    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        // STEP 1: Find order by ID
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

        // STEP 2: Update order status
        if(!order.getOrderStatus().equals("ORDER_CANCELLED")){
            order.setOrderStatus("Order Cancelled");
            orderRepository.save(order);
        }

        // STEP 3: Prepare OrderDto
        OrderDto orderDto = new OrderDto();
        orderDto.setId(order.getId());
        orderDto.setTotalPrice(order.getTotalPrice());
        orderDto.setOrderDate(order.getOrderDate());
        orderDto.setOrderItems(
                order.getOrderItems().stream()
                        .map(item -> {
                            OrderItemDto dto = new OrderItemDto();
                            dto.setSkuCode(item.getProductId());
                            dto.setQuantity(item.getQuantity());
                            return dto;
                        })
                        .collect(Collectors.toList())
        );


        // STEP 4: 🔥 KAFKA PRODUCER CALLED HERE!
        // ✅ THIS IS WHERE KAFKA PRODUCER IS CALLED
        orderProducer.sendOrderEvent(orderDto, "cancelled");

        return new OrderResponse(order);
    }

    public OrderItem convertToEntity(OrderItemDto itemDto) {
        OrderItem orderItem = new OrderItem();
        orderItem.setProductId(itemDto.getSkuCode());
        orderItem.setProductName(itemDto.getProductName());
        orderItem.setQuantity(itemDto.getQuantity());
        orderItem.setPrice(itemDto.getPrice());
        return orderItem;

    }

    public Order convertToEntity(OrderDto orderDto) {
        Order order = new Order();
        order.setId(orderDto.getId());
        order.setOrderDate(orderDto.getOrderDate());
        order.setTotalPrice(orderDto.getTotalPrice());
        return order;
    }

    public OrderDto convertToDto(Order order) {
        OrderDto orderDto = new OrderDto();
        orderDto.setId(order.getId());
        orderDto.setOrderDate(order.getOrderDate());
        orderDto.setTotalPrice(order.getTotalPrice());
        List<OrderItemDto> itemDtos = order.getOrderItems().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        orderDto.setOrderItems(itemDtos);
        return orderDto;
    }

    public boolean isProductAvailable(String skuCode, List<ProductAvailability> availabilityList) {
        return availabilityList.stream()
                .anyMatch(availability -> availability.getSkuCode().equals(skuCode) && availability.isAvailable());
    }

}