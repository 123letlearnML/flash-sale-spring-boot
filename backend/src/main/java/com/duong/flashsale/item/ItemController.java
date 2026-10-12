package com.duong.flashsale.item;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ItemController {

    public record ResetRequest(@Min(1) @Max(1_000_000) int stock) {
    }

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping("/items")
    public List<ItemStats> list() {
        return itemService.listStats();
    }

    @GetMapping("/items/{itemId}/stats")
    public ItemStats stats(@PathVariable long itemId) {
        return itemService.stats(itemId);
    }

    /** Endpoint quản trị — hệ thống thật phải bảo vệ bằng phân quyền ADMIN. */
    @PostMapping("/admin/items/{itemId}/reset")
    public ItemStats reset(@PathVariable long itemId, @Valid @RequestBody ResetRequest request) {
        return itemService.reset(itemId, request.stock());
    }
}
