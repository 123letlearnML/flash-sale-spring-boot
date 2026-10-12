package com.duong.flashsale.order;

import com.duong.flashsale.purchase.StrategyType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Chỉ giữ userId/itemId dạng số thay vì @ManyToOne: tránh lazy-loading và query thừa trên đường nóng.
 */
@Entity
@Table(name = "orders")
public class PurchaseOrder {

    public enum Status { CONFIRMED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;

    private long itemId;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Enumerated(EnumType.STRING)
    private StrategyType strategy;

    private Instant createdAt;

    protected PurchaseOrder() {
    }

    public PurchaseOrder(long userId, long itemId, StrategyType strategy) {
        this.userId = userId;
        this.itemId = itemId;
        this.status = Status.CONFIRMED;
        this.strategy = strategy;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public long getItemId() {
        return itemId;
    }

    public Status getStatus() {
        return status;
    }

    public StrategyType getStrategy() {
        return strategy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
