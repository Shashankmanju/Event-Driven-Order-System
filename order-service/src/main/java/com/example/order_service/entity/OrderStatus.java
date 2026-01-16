package com.example.order_service.entity;

public enum OrderStatus {
    ORDER_PLACED,
    ORDER_CANCELLED;

    @Override
    public String toString() {
        return "OrderStatus{}";
    }
}