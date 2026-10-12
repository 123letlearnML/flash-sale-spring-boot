package com.duong.flashsale.purchase;

public record PurchaseResult(Outcome outcome, Long orderId) {

    public enum Outcome {
        SUCCESS,
        SOLD_OUT,
        ALREADY_PURCHASED,
        /** Optimistic lock: retry hết số lần cho phép mà vẫn xung đột. */
        RETRY_EXHAUSTED
    }

    public static PurchaseResult success(long orderId) {
        return new PurchaseResult(Outcome.SUCCESS, orderId);
    }

    public static PurchaseResult soldOut() {
        return new PurchaseResult(Outcome.SOLD_OUT, null);
    }

    public static PurchaseResult alreadyPurchased() {
        return new PurchaseResult(Outcome.ALREADY_PURCHASED, null);
    }

    public static PurchaseResult retryExhausted() {
        return new PurchaseResult(Outcome.RETRY_EXHAUSTED, null);
    }
}
