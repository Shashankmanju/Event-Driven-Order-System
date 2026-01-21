# 🛒 Complete Order Creation Flow - End-to-End Architecture

---

## 🎯 Overview

This document explains the complete journey of a user creating an order, from the initial HTTP request to both services (Order & Product) interacting to successfully place the order.

---

## 📊 Complete System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          MICROSERVICES ECOSYSTEM                             │
│                                                                              │
│  ┌──────────────────────────┐              ┌──────────────────────────┐    │
│  │   ORDER SERVICE          │              │   PRODUCT SERVICE        │    │
│  │   Port: 8053             │◄────REST────►│   Port: 8051             │    │
│  │                          │              │                          │    │
│  │  - OrderController       │              │  - ProductController     │    │
│  │  - OrderService          │              │  - ProductService        │    │
│  │  - OrderRepository       │              │  - ProductRepository     │    │
│  │  - OrderProducer (Kafka) │              │  - ProductConsumer       │    │
│  │                          │              │                          │    │
│  │  PostgreSQL: order_db    │              │  PostgreSQL: inventory   │    │
│  └──────────┬───────────────┘              └──────────┬───────────────┘    │
│             │                                          │                     │
│             │         ┌─────────────────┐             │                     │
│             └────────►│  KAFKA BROKER   │◄────────────┘                     │
│                       │  Port: 9092     │                                   │
│                       │                 │                                   │
│                       │  Topics:        │                                   │
│                       │  - order_placed │                                   │
│                       │  - order_cancelled │                                │
│                       └─────────────────┘                                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Complete Order Creation Flow (Step-by-Step)

### 📍 STEP 1: User Sends Order Request

```
USER/CLIENT
   │
   │ POST http://localhost:8053/create
   │
   │ Request Body (JSON):
   │ {
   │   "totalPrice": 1999.98,
   │   "orderDate": "2026-01-03T10:30:00",
   │   "orderItems": [
   │     {
   │       "skuCode": "LAPTOP-001",
   │       "productName": "Dell Laptop",
   │       "quantity": 2,
   │       "price": 999.99
   │     }
   │   ]
   │ }
   │
   ▼
ORDER SERVICE - OrderController.createOrder()
```

**What happens:**
* User makes HTTP POST request to Order Service
* Request contains order details and list of products to order
* OrderController receives the request

---

### 📍 STEP 2: Order Service Validates Stock with Product Service

```
ORDER SERVICE (OrderController)
   │
   │ Step 2.1: Check if products are available
   │
   ▼
ProductServiceClient.checkProductAvailability(orderItems)
   │
   │ Makes REST API call to Product Service
   │
   ▼
HTTP POST → http://localhost:8051/api/products/availability
   │
   │ Request Body:
   │ [
   │   {
   │     "skuCode": "LAPTOP-001",
   │     "quantity": 2
   │   }
   │ ]
   │
   ▼
PRODUCT SERVICE - ProductController.checkProductAvailability()
```

**This is WHERE the availability check happens!**

---

### 📍 STEP 3: Product Service Checks Database

```
PRODUCT SERVICE
   │
   ▼
ProductController.checkProductAvailability()
   │
   ▼
ProductService.checkProductAvailability()
   │
   │ For each product in request:
   │ 1. Find product by SKU code in database
   │ 2. Check: product.quantity >= requested quantity
   │
   ▼
ProductRepository.findBySkuCode("LAPTOP-001")
   │
   ▼
PostgreSQL Database (inventory)
   │
   │ Query: SELECT * FROM products WHERE sku_code = 'LAPTOP-001'
   │ Result: { id: 1, skuCode: "LAPTOP-001", quantity: 50, ... }
   │
   ▼
Availability Check Logic:
   │
   │ Product in DB: quantity = 50
   │ Requested: quantity = 2
   │
   │ 50 >= 2 ? YES ✅
   │
   ▼
Build Response:
   │
   │ {
   │   "productAvailabilityList": [
   │     {
   │       "skuCode": "LAPTOP-001",
   │       "available": true
   │     }
   │   ]
   │ }
   │
   ▼
Return Response to Order Service
```

**Important:** At this point, NO stock is reduced. We're just checking availability!

---

### 📍 STEP 4: Order Service Receives Availability Response

```
ORDER SERVICE (OrderController)
   │
   │ Receives response from Product Service:
   │ [
   │   { "skuCode": "LAPTOP-001", "available": true }
   │ ]
   │
   ▼
Filter Available Items:
   │
   │ availableItems = orderDto.getOrderItems()
   │     .filter(item -> isProductAvailable(item.skuCode, availabilityList))
   │
   │ Result:
   │ - LAPTOP-001: available = true ✅
   │ - Keep this item in the order
   │
   ▼
Check if any items are available:
   │
   │ if (availableItems.isEmpty()) {
   │   ❌ Return error: "No items available"
   │ }
   │
   │ else {
   │   ✅ Continue with order creation
   │ }
   │
   ▼
Update orderDto with only available items
```

**What happens:**
* Order Service filters out unavailable products
* Only available products proceed to order creation
* If all products unavailable → Return error to user

---

### 📍 STEP 5: Save Order to Database

```
ORDER SERVICE
   │
   ▼
OrderService.createOrder(orderDto)
   │
   │ Convert OrderDto to Order Entity
   │
   ▼
Create Order Entity:
   │
   │ Order order = new Order()
   │ order.setTotalPrice(1999.98)
   │ order.setOrderDate(2026-01-03T10:30:00)
   │ order.setOrderStatus("ORDER_PLACED")
   │
   ▼
Create OrderItems:
   │
   │ For each item in orderDto.orderItems:
   │   OrderItem item = new OrderItem()
   │   item.setProductId("LAPTOP-001")
   │   item.setProductName("Dell Laptop")
   │   item.setQuantity(2)
   │   item.setPrice(999.99)
   │   item.setOrder(order)
   │
   ▼
OrderRepository.save(order)
   │
   ▼
PostgreSQL Database (order_db)
   │
   │ INSERT INTO orders (total_price, order_date, order_status)
   │ VALUES (1999.98, '2026-01-03 10:30:00', 'ORDER_PLACED');
   │
   │ INSERT INTO order_items (product_id, product_name, quantity, price, order_id)
   │ VALUES ('LAPTOP-001', 'Dell Laptop', 2, 999.99, 1);
   │
   ▼
Order Saved! Order ID: 1
```

**What happens:**
* Order is created with status "ORDER_PLACED"
* Order and OrderItems saved to database
* Returns OrderResponse with orderId

---

### 📍 STEP 6: Publish Event to Kafka

```
ORDER SERVICE (OrderController)
   │
   │ Order created successfully!
   │ orderResponse.orderId = 1
   │
   ▼
OrderProducer.sendOrderEvent(orderDto, "placed")
   │
   │ Topic: "order_placed"
   │ Message: Full order details (OrderDto)
   │
   ▼
KAFKA BROKER
   │
   │ Message published to topic: order_placed
   │ {
   │   "id": 1,
   │   "totalPrice": 1999.98,
   │   "orderDate": "2026-01-03T10:30:00",
   │   "orderItems": [
   │     {
   │       "skuCode": "LAPTOP-001",
   │       "quantity": 2
   │     }
   │   ]
   │ }
   │
   ▼
Event stored in Kafka topic
```

**What happens:**
* Order event published to Kafka asynchronously
* Order Service doesn't wait for Product Service to process
* Event sits in Kafka queue

---

### 📍 STEP 7: Product Service Consumes Kafka Event

```
KAFKA BROKER
   │
   │ Topic: order_placed has new message
   │
   ▼
PRODUCT SERVICE - ProductConsumer
   │
   │ @KafkaListener(topics = "order_placed")
   │ Automatically triggered when message arrives
   │
   ▼
ProductConsumer.consumeOrderPlaced(orderMessage)
   │
   │ 1. Deserialize JSON message to OrderMessage object
   │ 2. Extract order items
   │
   ▼
Loop through each order item:
   │
   │ For item in orderItems:
   │   skuCode = "LAPTOP-001"
   │   quantity = 2
   │
   ▼
ProductService.reduceProductQuantity("LAPTOP-001", 2)
   │
   ▼
ProductRepository.findBySkuCode("LAPTOP-001")
   │
   ▼
PostgreSQL Database (inventory)
   │
   │ Current product: { skuCode: "LAPTOP-001", quantity: 50 }
   │
   ▼
Update Stock:
   │
   │ Check: 50 >= 2 ? YES ✅
   │ New quantity = 50 - 2 = 48
   │ product.setQuantity(48)
   │
   ▼
ProductRepository.save(product)
   │
   ▼
PostgreSQL Database (inventory)
   │
   │ UPDATE products
   │ SET quantity = 48
   │ WHERE sku_code = 'LAPTOP-001';
   │
   ▼
✅ Stock reduced successfully!
   │
   │ Log: "Reduced stock for SKU: LAPTOP-001"
```

**What happens:**
* Product Service listens to Kafka automatically
* When event arrives, it processes the order
* Stock is ACTUALLY reduced here
* This happens asynchronously (Order Service already responded to user)

---

### 📍 STEP 8: Return Response to User

```
ORDER SERVICE (OrderController)
   │
   │ Order saved in database ✅
   │ Kafka event published ✅
   │
   ▼
Return Response to User:
   │
   │ HTTP 201 CREATED
   │ {
   │   "orderId": 1,
   │   "status": "ORDER PLACED"
   │ }
   │
   ▼
USER receives confirmation
```

**What happens:**
* User gets immediate confirmation that order is placed
* User doesn't wait for stock reduction (happens asynchronously)
* Faster response time for better user experience

---

## 🔍 Detailed Timeline: When Does Each Action Happen?

| Time    | Service         | Action                                  | Synchronous/Async |
|---------|-----------------|----------------------------------------|-------------------|
| T+0ms   | Order Service   | Receives POST request                  | Sync              |
| T+50ms  | Order Service   | Calls Product Service REST API         | Sync (waits)      |
| T+100ms | Product Service | Checks availability in database        | Sync              |
| T+150ms | Product Service | Returns availability response          | Sync              |
| T+200ms | Order Service   | Filters available items                | Sync              |
| T+250ms | Order Service   | Saves order to database                | Sync              |
| T+300ms | Order Service   | Publishes event to Kafka               | Async (no wait)   |
| T+310ms | Order Service   | Returns response to user               | Sync              |
| T+500ms | Product Service | Consumes Kafka event                   | Async             |
| T+550ms | Product Service | Reduces stock in database              | Async             |

---

## 🎯 Key Question Answered: When is Availability Checked?

**Availability Check Happens in STEP 2:**

```
┌─────────────────────────────────────────────────────────────┐
│  SYNCHRONOUS CALL (Order Service → Product Service)        │
│                                                              │
│  Order Service:                                             │
│  ProductServiceClient.checkProductAvailability()            │
│         │                                                    │
│         │ HTTP POST (REST API Call)                        │
│         │                                                    │
│         ▼                                                    │
│  Product Service:                                           │
│  POST /api/products/availability                            │
│         │                                                    │
│         ▼                                                    │
│  ProductService.checkProductAvailability()                  │
│         │                                                    │
│         ▼                                                    │
│  For each product:                                          │
│    1. Find product by SKU in database                       │
│    2. Compare: DB quantity >= requested quantity            │
│    3. Return: { skuCode, available: true/false }            │
│         │                                                    │
│         │ HTTP Response                                     │
│         ▼                                                    │
│  Order Service receives:                                    │
│  [{ "skuCode": "LAPTOP-001", "available": true }]          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Why Synchronous?

* Order Service MUST know if products are available BEFORE creating the order
* If products unavailable → Don't create order, return error
* If products available → Continue with order creation

---

## 📦 Database State Changes

### Before Order:

**Order Service DB (order_db):**
- `orders` table: (empty)
- `order_items` table: (empty)

**Product Service DB (inventory):**

```
products table:
+----+-------------+-------------+----------+--------+
| id | name        | skuCode     | quantity | price  |
+----+-------------+-------------+----------+--------+
| 1  | Dell Laptop | LAPTOP-001  | 50       | 999.99 |
+----+-------------+-------------+----------+--------+
```

---

### After STEP 5 (Order Saved):

**Order Service DB (order_db):**

```
orders table:
+----+-------------+---------------------+--------------+
| id | total_price | order_date          | order_status |
+----+-------------+---------------------+--------------+
| 1  | 1999.98     | 2026-01-03 10:30:00 | ORDER_PLACED |
+----+-------------+---------------------+--------------+

order_items table:
+----+-------------+--------------+----------+--------+----------+
| id | product_id  | product_name | quantity | price  | order_id |
+----+-------------+--------------+----------+--------+----------+
| 1  | LAPTOP-001  | Dell Laptop  | 2        | 999.99 | 1        |
+----+-------------+--------------+----------+--------+----------+
```

**Product Service DB (inventory):**

```
products table: (NO CHANGE YET)
+----+-------------+-------------+----------+--------+
| id | name        | skuCode     | quantity | price  |
+----+-------------+-------------+----------+--------+
| 1  | Dell Laptop | LAPTOP-001  | 50       | 999.99 |
+----+-------------+-------------+----------+--------+
```

---

### After STEP 7 (Stock Reduced):

**Order Service DB (order_db):**
- (No change - same as above)

**Product Service DB (inventory):**

```
products table: (QUANTITY REDUCED)
+----+-------------+-------------+----------+--------+
| id | name        | skuCode     | quantity | price  |
+----+-------------+-------------+----------+--------+
| 1  | Dell Laptop | LAPTOP-001  | 48       | 999.99 | ← Changed!
+----+-------------+-------------+----------+--------+
```

---

## 🔄 What if Order is Cancelled?

### Cancel Order Flow:

```
USER
   │
   │ PUT http://localhost:8053/cancel/1
   │
   ▼
ORDER SERVICE (OrderController.cancelOrder)
   │
   ▼
OrderService.cancelOrder(orderId)
   │
   │ 1. Find order by ID
   │ 2. Update status to "ORDER_CANCELLED"
   │ 3. Save to database
   │
   ▼
OrderProducer.sendOrderEvent(orderDto, "cancelled")
   │
   │ Topic: "order_cancelled"
   │
   ▼
KAFKA BROKER
   │
   │ Message published to: order_cancelled
   │
   ▼
PRODUCT SERVICE (ProductConsumer)
   │
   │ @KafkaListener(topics = "order_cancelled")
   │
   ▼
ProductConsumer.consumeOrderCancelled()
   │
   ▼
ProductService.increaseProductQuantity("LAPTOP-001", 2)
   │
   │ Current: 48
   │ Add back: 2
   │ New: 48 + 2 = 50
   │
   ▼
Stock restored to 50 ✅
```

---

## 🏗️ Architecture Patterns Used

### 1. Synchronous Communication (REST API)

**Used for:** Checking product availability  
**Why:** Order Service needs immediate answer before proceeding

```
Order Service ──(REST API)──→ Product Service
              ←─(Response)───
```

---

### 2. Asynchronous Communication (Kafka)

**Used for:** Stock reduction after order placement  
**Why:** User doesn't need to wait for stock update

```
Order Service ──(Kafka Event)──→ Kafka Broker
                                      │
                                      ▼
                                Product Service
                                (processes later)
```

---

### 3. Event-Driven Architecture

* Order Service publishes events
* Product Service subscribes to events
* Services are loosely coupled
* Can add more consumers without changing Order Service

---

## 📋 Configuration Details

### Order Service Configuration:

```properties
# Server
server.port=8053

# Database
PostgreSQL database: order_db
Tables: orders, order_items

# Product Service URL
product.service.url=http://localhost:8051/api/products

# Kafka
spring.kafka.bootstrap-servers=localhost:9092
Role: Producer (publishes events)
Topics: order_placed, order_cancelled
```

---

### Product Service Configuration:

```properties
# Server
server.port=8051

# Database
PostgreSQL database: inventory
Table: products

# Kafka
spring.kafka.bootstrap-servers=localhost:9092
Role: Consumer (listens to events)
Topics: order_placed, order_cancelled
Group ID: product-service-group
```

---

## 🎯 Summary

### Complete Flow in Simple Terms:

1. **User creates order** → Sends request to Order Service
2. **Order Service checks stock** → Calls Product Service REST API (SYNCHRONOUS)
3. **Product Service checks database** → Returns availability (available/not available)
4. **Order Service filters items** → Only keeps available products
5. **Order Service saves order** → Stores in database with status "ORDER_PLACED"
6. **Order Service publishes event** → Sends to Kafka (ASYNCHRONOUS)
7. **User gets response** → "Order placed successfully"
8. **Product Service listens to Kafka** → Automatically triggered
9. **Product Service reduces stock** → Updates database
10. **Done!** → Order placed, stock reduced

---

## ⚡ Key Insights

### Why Two Communication Methods?

#### 1. REST API (Sync) - For availability check
* MUST know availability before creating order
* Need immediate response
* Blocking call (waits for answer)

#### 2. Kafka (Async) - For stock reduction
* User doesn't need to wait
* Better performance
* Non-blocking (fire and forget)
* Can retry if Product Service is down

### Benefits:
* ✅ Fast user response
* ✅ Reliable stock management
* ✅ Services are independent
* ✅ Can scale independently
* ✅ Fault tolerant (Kafka stores events if service is down)

---

## 🚨 Edge Cases Handled

### Case 1: Product Not Available

```
Product has 1 in stock, user orders 5
→ Availability check returns: available = false
→ Order Service filters out this item
→ If all items unavailable: Return error to user
→ No order created ✅
```

---

### Case 2: Multiple Products, Some Available

```
Product A: Available ✅
Product B: Not available ❌
→ Order created with only Product A
→ User gets partial order confirmation
→ Only Product A stock is reduced
```

---

### Case 3: Product Service Down During Availability Check

```
Order Service calls Product Service → No response (timeout)
→ ProductServiceClient catches exception
→ Returns empty availability list
→ Order Service: "No items available"
→ User gets error ✅
```

---

### Case 4: Product Service Down During Stock Reduction

```
Order already created and confirmed to user
→ Kafka event published to order_placed topic
→ Product Service is down (not consuming)
→ Event stays in Kafka queue
→ When Product Service comes back up: Processes all pending events
→ Stock reduced correctly ✅
```

---

## 📊 Performance Characteristics

**User Experience:** Order confirmed in ~200ms (fast!)  
**Actual Stock Update:** Happens ~500ms later (transparent to user)

---

This is the complete end-to-end architecture of your Order Management System! 🎉

#   E v e n t - D r i v e n - O r d e r - S y s t e m  
 #   E v e n t - D r i v e n - O r d e r - S y s t e m  
 