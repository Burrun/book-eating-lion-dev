package com.bookeatinglion.order.inventory.controller;

import com.bookeatinglion.order.inventory.dto.InventoryView;
import com.bookeatinglion.order.inventory.dto.RestockRequest;
import com.bookeatinglion.order.inventory.service.InventoryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /internal/** 은 서비스 간 전용이다. 외부에는 노출하지 않는다.
 *  - Ingress 는 /api/** 만 라우팅한다 (k8s/base/08-ingress.yaml)
 *  - NetworkPolicy 가 클러스터 밖에서 오는 트래픽을 차단한다 (k8s/base/09-networkpolicy.yaml)
 *
 * 여기 있는 두 개가 inbound 동기 통신 전부다(계획서 §7.5). outbound 는 CatalogClient 와
 * CardClient 가 §7.6 예외다(OrderApiApplication 참고).
 */
@RestController
@RequestMapping("/internal/inventory")
@RequiredArgsConstructor
public class InternalInventoryController {

    private final InventoryService inventoryService;

    /** 반드시 벌크. 도서 20건 렌더링에 호출 1회. */
    @GetMapping
    public List<InventoryView> findByBookIds(@RequestParam List<Long> bookIds) {
        return inventoryService.findByBookIds(bookIds);
    }

    @PostMapping("/{bookId}/restock")
    public InventoryView restock(@PathVariable Long bookId, @Valid @RequestBody RestockRequest request) {
        return inventoryService.restock(bookId, request.quantity());
    }
}
