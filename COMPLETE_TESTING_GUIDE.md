# 🚀 Complete Order Management System Testing Guide

## 📋 Table of Contents
1. [Prerequisites](#prerequisites)
2. [Architecture Overview](#architecture-overview)
3. [Service Startup Order](#service-startup-order)
4. [Testing Flow](#testing-flow)
5. [API Endpoints with Sample Data](#api-endpoints-with-sample-data)
6. [End-to-End Testing Scenarios](#end-to-end-testing-scenarios)
7. [Troubleshooting](#troubleshooting)

---

## 🔧 Prerequisites

### Required Software
- ✅ **Java 17** or higher
- ✅ **PostgreSQL** (for both services)
- ✅ **Apache Kafka** (with Zookeeper)
- ✅ **Maven** (or use provided mvnw)
- ✅ **Postman** or **cURL** (for API testing)

### Database Setup
Create two PostgreSQL databases:
```sql
-- Connect to PostgreSQL
psql -U postgres

-- Create databases
CREATE DATABASE order_db;
CREATE DATABASE inventory;

-- Verify
\l
```

---

## 🏗️ Architecture Overview

```
┌─────────────────┐         ┌──────────────────┐
│  Order Service  │◄───────►│ Product Service  │
│   Port: 8053    │  HTTP   │   Port: 8051     │
└────────┬────────┘         └────────┬─────────┘
         │                           │
         │  Kafka Topics:            │
         │  - order_placed           │
         │  - order_cancelled        │
         └───────────┬───────────────┘
                     │
              ┌──────▼──────┐
              │    Kafka    │
              │  Port: 9092 │
              └─────────────┘

Databases:
- Order Service → order_db (PostgreSQL)
- Product Service → inventory (PostgreSQL)
```

---

## 📦 Service Startup Order

### Step 1: Start PostgreSQL
```bash
# Check if PostgreSQL is running
psql -U postgres -c "SELECT version();"

# If not running, start it (macOS)
brew services start postgresql

# Create databases if not exists
psql -U postgres -c "CREATE DATABASE order_db;"
psql -U postgres -c "CREATE DATABASE inventory;"
```

### Step 2: Start Zookeeper
```bash
# Navigate to Kafka directory
cd /path/to/kafka

# Start Zookeeper
bin/zookeeper-server-start.sh config/zookeeper.properties
```

### Step 3: Start Kafka
```bash
# In a new terminal, navigate to Kafka directory
cd /path/to/kafka

# Start Kafka
bin/kafka-server-start.sh config/server.properties
```

### Step 4: Create Kafka Topics
```bash
# Create order_placed topic
bin/kafka-topics.sh --create \
  --topic order_placed \
  --bootstrap-server localhost:9092 \
  --partitions 1 \
  --replication-factor 1

# Create order_cancelled topic
bin/kafka-topics.sh --create \
  --topic order_cancelled \
  --bootstrap-server localhost:9092 \
  --partitions 1 \
  --replication-factor 1

# Verify topics
bin/kafka-topics.sh --list --bootstrap-server localhost:9092
```

### Step 5: Start Product Service (MUST START FIRST)
```bash
cd product-service
./mvnw clean install
./mvnw spring-boot:run
```

**Wait for:** `Started ProductServiceApplication` message in logs

### Step 6: Start Order Service (START SECOND)
```bash
# In a new terminal
cd order-service
./mvnw clean install
./mvnw spring-boot:run
```

**Wait for:** `Started OrderServiceApplication` message in logs

---

## 🧪 Testing Flow

### Flow Diagram
```
1. Check Product Availability
   ↓
2. Create Order (if products available)
   ↓
3. Kafka Event: order_placed
   ↓
4. Product Service reduces inventory
   ↓
5. Cancel Order (optional)
   ↓
6. Kafka Event: order_cancelled
   ↓
7. Product Service restores inventory
```

---

## 📡 API Endpoints with Sample Data

### 🔹 PRODUCT SERVICE (Port: 8051)

#### 1. Get Product by ID
**Endpoint:** `GET http://localhost:8051/api/products/{id}`

**Example:**
```bash
curl http://localhost:8051/api/products/1
```

**Expected Response:**
```json
{
  "id": 1,
  "name": "Samsung Refrigerator",
  "description": "Double door refrigerator",
  "skuCode": "samref002",
  "price": 45000.0,
  "quantity": 5
}
```

---

#### 2. Create Product
**Endpoint:** `POST http://localhost:8051/api/products`

**Request Body:**
```json
{
  "name": "Dell Laptop",
  "description": "Intel i7, 16GB RAM, 512GB SSD",
  "skuCode": "dell-lap-001",
  "price": 85000.0,
  "quantity": 10
}
```

**cURL:**
```bash
curl -X POST http://localhost:8051/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Dell Laptop",
    "description": "Intel i7, 16GB RAM, 512GB SSD",
    "skuCode": "dell-lap-001",
    "price": 85000.0,
    "quantity": 10
  }'
```

**Expected Response:**
```json
{
  "id": 10,
  "name": "Dell Laptop",
  "description": "Intel i7, 16GB RAM, 512GB SSD",
  "skuCode": "dell-lap-001",
  "price": 85000.0,
  "quantity": 10
}
```

---

#### 3. Update Product
**Endpoint:** `PUT http://localhost:8051/api/products/{id}`

**Request Body:**
```json
{
  "name": "Samsung Refrigerator",
  "description": "Double door refrigerator with ice maker",
  "skuCode": "samref002",
  "price": 48000.0,
  "quantity": 8
}
```

**cURL:**
```bash
curl -X PUT http://localhost:8051/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Samsung Refrigerator",
    "description": "Double door refrigerator with ice maker",
    "skuCode": "samref002",
    "price": 48000.0,
    "quantity": 8
  }'
```

---

#### 4. ⭐ Check Product Availability (CRITICAL FOR ORDER CREATION)
**Endpoint:** `POST http://localhost:8051/api/products/availability`

**Request Body:**
```json
[
  {
    "skuCode": "samref002",
    "quantity": 2
  },
  {
    "skuCode": "sonytv003",
    "quantity": 1
  }
]
```

**cURL:**
```bash
curl -X POST http://localhost:8051/api/products/availability \
  -H "Content-Type: application/json" \
  -d '[
    {
      "skuCode": "samref002",
      "quantity": 2
    },
    {
      "skuCode": "sonytv003",
      "quantity": 1
    }
  ]'
```

**Expected Response:**
```json
{
  "productAvailabilityList": [
    {
      "skuCode": "samref002",
      "available": true
    },
    {
      "skuCode": "sonytv003",
      "available": true
    }
  ]
}
```

---

### 🔹 ORDER SERVICE (Port: 8053)

#### 5. ⭐ Create Order (MAIN ENDPOINT)
**Endpoint:** `POST http://localhost:8053/create`

**Request Body:**
```json
{
  "orderItems": [
    {
      "skuCode": "samref002",
      "productName": "Samsung Refrigerator",
      "quantity": 2,
      "price": 45000
    },
    {
      "skuCode": "sonytv003",
      "productName": "Sony LED TV",
      "quantity": 1,
      "price": 60000
    }
  ],
  "totalPrice": 150000
}
```

**cURL:**
```bash
curl -X POST http://localhost:8053/create \
  -H "Content-Type: application/json" \
  -d '{
    "orderItems": [
      {
        "skuCode": "samref002",
        "productName": "Samsung Refrigerator",
        "quantity": 2,
        "price": 45000
      },
      {
        "skuCode": "sonytv003",
        "productName": "Sony LED TV",
        "quantity": 1,
        "price": 60000
      }
    ],
    "totalPrice": 150000
  }'
```

**Expected Response:**
```json
{
  "orderId": 1,
  "message": "Order created successfully",
  "orderStatus": "PLACED"
}
```

**What Happens Behind the Scenes:**
1. ✅ Order Service checks product availability with Product Service
2. ✅ Order is saved to `order_db` database
3. ✅ Kafka event `order_placed` is published
4. ✅ Product Service consumes the event
5. ✅ Product inventory is reduced automatically

---

#### 6. ⭐ Cancel Order
**Endpoint:** `PUT http://localhost:8053/cancel/{orderId}`

**Example:**
```bash
curl -X PUT http://localhost:8053/cancel/1
```

**Expected Response:**
```json
{
  "orderId": 1,
  "message": "Order cancelled successfully",
  "orderStatus": "CANCELLED"
}
```

**What Happens Behind the Scenes:**
1. ✅ Order status is updated to `CANCELLED` in database
2. ✅ Kafka event `order_cancelled` is published
3. ✅ Product Service consumes the event
4. ✅ Product inventory is restored automatically

---

## 🎯 End-to-End Testing Scenarios

### Scenario 1: Successful Order Flow ✅

**Step 1:** Check available products
```bash
curl http://localhost:8051/api/products/1
```

**Step 2:** Check availability for order
```bash
curl -X POST http://localhost:8051/api/products/availability \
  -H "Content-Type: application/json" \
  -d '[{"skuCode": "samref002", "quantity": 2}]'
```

**Step 3:** Create order
```bash
curl -X POST http://localhost:8053/create \
  -H "Content-Type: application/json" \
  -d '{
    "orderItems": [
      {
        "skuCode": "samref002",
        "productName": "Samsung Refrigerator",
        "quantity": 2,
        "price": 45000
      }
    ],
    "totalPrice": 90000
  }'
```

**Step 4:** Verify inventory reduced (wait 2-3 seconds for Kafka processing)
```bash
curl http://localhost:8051/api/products/1
# Quantity should be reduced by 2
```

---

### Scenario 2: Order Cancellation Flow ✅

**Step 1:** Note current inventory
```bash
curl http://localhost:8051/api/products/1
# Example: quantity = 3
```

**Step 2:** Cancel the order
```bash
curl -X PUT http://localhost:8053/cancel/1
```

**Step 3:** Verify inventory restored (wait 2-3 seconds)
```bash
curl http://localhost:8051/api/products/1
# Quantity should be increased back by 2 (now 5)
```

---

### Scenario 3: Insufficient Inventory ❌

**Request:**
```bash
curl -X POST http://localhost:8053/create \
  -H "Content-Type: application/json" \
  -d '{
    "orderItems": [
      {
        "skuCode": "samref002",
        "productName": "Samsung Refrigerator",
        "quantity": 100,
        "price": 45000
      }
    ],
    "totalPrice": 4500000
  }'
```

**Expected Response:**
```json
{
  "message": "No items available for the order."
}
```

---

### Scenario 4: Multiple Products Order ✅

**Request:**
```bash
curl -X POST http://localhost:8053/create \
  -H "Content-Type: application/json" \
  -d '{
    "orderItems": [
      {
        "skuCode": "samref002",
        "productName": "Samsung Refrigerator",
        "quantity": 1,
        "price": 45000
      },
      {
        "skuCode": "sonytv003",
        "productName": "Sony LED TV",
        "quantity": 2,
        "price": 60000
      },
      {
        "skuCode": "iph13pm005",
        "productName": "Apple iPhone",
        "quantity": 3,
        "price": 120000
      }
    ],
    "totalPrice": 525000
  }'
```

---

## 📊 Available Products (Pre-loaded via data.sql)

| ID | SKU Code    | Product Name            | Price    | Initial Qty |
|----|-------------|-------------------------|----------|-------------|
| 1  | samref002   | Samsung Refrigerator    | 45000    | 5           |
| 2  | sonytv003   | Sony LED TV             | 60000    | 8           |
| 3  | iph13pm005  | Apple iPhone            | 120000   | 20          |
| 4  | bosehd006   | Bose Headphones         | 25000    | 15          |
| 5  | candlr007   | Canon DSLR Camera       | 50000    | 12          |
| 6  | nikbin008   | Nikon Binoculars        | 20000    | 9           |
| 7  | phlaf009    | Philips Air Fryer       | 10000    | 10          |
| 8  | samgaw010   | Samsung Galaxy Watch    | 30000    | 25          |
| 9  | delmon011   | Dell Monitor            | 35000    | 18          |

---

## 🔍 Monitoring Kafka Events

### Monitor order_placed Topic
```bash
cd /path/to/kafka
bin/kafka-console-consumer.sh \
  --topic order_placed \
  --bootstrap-server localhost:9092 \
  --from-beginning
```

### Monitor order_cancelled Topic
```bash
bin/kafka-console-consumer.sh \
  --topic order_cancelled \
  --bootstrap-server localhost:9092 \
  --from-beginning
```

---

## 🐛 Troubleshooting

### Issue 1: "Connection refused" error
**Solution:** Ensure services are started in correct order:
1. PostgreSQL
2. Zookeeper
3. Kafka
4. Product Service
5. Order Service

### Issue 2: "Product not found" error
**Solution:** 
- Restart Product Service to reload data.sql
- Verify database has data: `psql -U postgres -d inventory -c "SELECT * FROM products;"`

### Issue 3: Inventory not updating after order
**Solution:**
- Check Kafka is running: `jps | grep Kafka`
- Monitor Kafka topics to see if events are published
- Check Product Service logs for consumer errors

### Issue 4: "No items available" even though products exist
**Solution:**
- Check product.service.url in order-service application.properties
- Verify Product Service is accessible: `curl http://localhost:8051/api/products/1`
- Check network/firewall settings

---

## 📝 Complete Test Checklist

- [ ] PostgreSQL databases created (order_db, inventory)
- [ ] Zookeeper started
- [ ] Kafka started
- [ ] Kafka topics created (order_placed, order_cancelled)
- [ ] Product Service started and data loaded
- [ ] Order Service started
- [ ] Can get product by ID
- [ ] Can check product availability
- [ ] Can create order successfully
- [ ] Inventory reduces after order (verify in DB or GET endpoint)
- [ ] Can cancel order
- [ ] Inventory restores after cancellation
- [ ] Kafka consumers processing events (check logs)

---

## 🎉 Success Indicators

✅ **Order Service Logs:**
```
Order placed successfully, sending 'Order Placed' event to Kafka...
```

✅ **Product Service Logs:**
```
Received ORDER_PLACED event for order ID: 1
Reducing inventory for SKU: samref002, Quantity: 2
```

✅ **Database Verification:**
```sql
-- Check order was created
SELECT * FROM orders WHERE id = 1;

-- Check inventory was reduced
SELECT * FROM products WHERE sku_code = 'samref002';
```

---

## 📞 Quick Reference

| Service          | Port | Health Check                          |
|------------------|------|---------------------------------------|
| Product Service  | 8051 | `curl http://localhost:8051/api/products/1` |
| Order Service    | 8053 | `curl -X POST http://localhost:8053/create` |
| Kafka            | 9092 | `nc -zv localhost 9092`              |
| PostgreSQL       | 5432 | `psql -U postgres -c "SELECT 1;"`    |

---

**Happy Testing! 🚀**

