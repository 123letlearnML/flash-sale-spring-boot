package com.duong.flashsale.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items/{itemId}/purchases")
public class PurchaseController {

    /**
     * userId nằm trong body để dễ load test. Hệ thống thật lấy userId từ JWT,
     * không bao giờ tin userId do client gửi lên.
     */
    public record PurchaseRequest(@NotNull @Positive Long userId, @NotNull StrategyType strategy) {
    }

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    public ResponseEntity<PurchaseResult> purchase(@PathVariable long itemId,
                                                   @Valid @RequestBody PurchaseRequest request) {
        PurchaseResult result = purchaseService.purchase(request.strategy(), request.userId(), itemId);
        HttpStatus status = result.outcome() == PurchaseResult.Outcome.SUCCESS
                ? HttpStatus.CREATED
                : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(result);
    }
}
