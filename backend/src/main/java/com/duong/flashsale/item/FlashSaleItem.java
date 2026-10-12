package com.duong.flashsale.item;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "flash_sale_item")
public class FlashSaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private long price;

    private int totalStock;

    private int availableStock;

    /**
     * Optimistic lock: Hibernate tự sinh "UPDATE ... SET version = v+1 WHERE id = ? AND version = v".
     * Nếu 0 dòng bị ảnh hưởng => có người khác đã sửa trước => ném ObjectOptimisticLockingFailureException.
     */
    @Version
    private long version;

    protected FlashSaleItem() {
    }

    public boolean isSoldOut() {
        return availableStock <= 0;
    }

    public void decreaseStock() {
        if (isSoldOut()) {
            throw new IllegalStateException("Item " + id + " is sold out");
        }
        availableStock--;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getPrice() {
        return price;
    }

    public int getTotalStock() {
        return totalStock;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public long getVersion() {
        return version;
    }
}
