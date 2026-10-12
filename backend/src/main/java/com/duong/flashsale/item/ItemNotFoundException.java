package com.duong.flashsale.item;

public class ItemNotFoundException extends RuntimeException {

    public ItemNotFoundException(long itemId) {
        super("Item " + itemId + " not found");
    }
}
