package com.roadrats.demo.controller.testtools;

import com.roadrats.demo.service.testtools.ItemImportService;
import com.roadrats.demo.service.testtools.OrderLookupService;
import com.roadrats.demo.service.testtools.OrderActionService;
import com.roadrats.demo.service.testtools.ShipOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test-tools")
public class TestToolsController {

    private static final Logger logger = LoggerFactory.getLogger(TestToolsController.class);

    private final OrderLookupService orderLookupService;
    private final ShipOrderService shipOrderService;
    private final OrderActionService orderActionService;
    private final ItemImportService itemImportService;

    public TestToolsController(OrderLookupService orderLookupService, ShipOrderService shipOrderService,
                               OrderActionService orderActionService, ItemImportService itemImportService) {
        this.orderLookupService = orderLookupService;
        this.shipOrderService = shipOrderService;
        this.orderActionService = orderActionService;
        this.itemImportService = itemImportService;
    }

    /**
     * GET /api/test-tools/lookup?type=order&value=xxx&warehouseId=CFF1
     * Search types: order, container, oms
     * warehouseId is required for order/container, optional for oms
     */
    @GetMapping("/lookup")
    public ResponseEntity<?> lookupOrder(
            @RequestParam String type,
            @RequestParam String value,
            @RequestParam(required = false, defaultValue = "") String warehouseId,
            @RequestParam(required = false, defaultValue = "both") String stack) {
        logger.info("GET /api/test-tools/lookup?type={}&value={}&warehouseId={}&stack={}", type, value, warehouseId, stack);

        if (value == null || value.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Search value is required"));
        }

        if (!"oms".equals(type) && (warehouseId == null || warehouseId.trim().isEmpty())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Warehouse ID is required for order/container search"));
        }

        String normalizedStack = stack.trim().toLowerCase();
        if (!List.of("aad", "io", "both").contains(normalizedStack)) {
            normalizedStack = "both";
        }

        try {
            Map<String, Object> result = orderLookupService.lookupOrder(type.trim(), value.trim(), warehouseId.trim(), normalizedStack);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error during order lookup", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Failed to perform lookup");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * POST /api/test-tools/ship-order
     * Body: { "warehouseId": "CFF1", "orderNumber": "xxx" }
     */
    @PostMapping("/ship-order")
    public ResponseEntity<?> shipOrder(@RequestBody Map<String, String> request) {
        String warehouseId = request.get("warehouseId");
        String orderNumber = request.get("orderNumber");

        logger.info("POST /api/test-tools/ship-order wh={}, order={}", warehouseId, orderNumber);

        if (warehouseId == null || warehouseId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "warehouseId is required"));
        }
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "orderNumber is required"));
        }

        try {
            Map<String, Object> result = shipOrderService.shipOrder(warehouseId.trim(), orderNumber.trim());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error shipping order", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Failed to ship order");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * POST /api/test-tools/ship-container
     * Body: { "warehouseId": "CFF1", "containerId": "xxx" }
     */
    @PostMapping("/ship-container")
    public ResponseEntity<?> shipContainer(@RequestBody Map<String, String> request) {
        String warehouseId = request.get("warehouseId");
        String containerId = request.get("containerId");

        logger.info("POST /api/test-tools/ship-container wh={}, container={}", warehouseId, containerId);

        if (warehouseId == null || warehouseId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "warehouseId is required"));
        }
        if (containerId == null || containerId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "containerId is required"));
        }

        try {
            Map<String, Object> result = shipOrderService.shipContainer(warehouseId.trim(), containerId.trim());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error shipping container", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Failed to ship container");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * GET /api/test-tools/connection-test
     * Test database connectivity
     */
    @GetMapping("/connection-test")
    public ResponseEntity<?> testConnection() {
        logger.info("GET /api/test-tools/connection-test");
        try {
            return ResponseEntity.ok(orderLookupService.testConnection());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/test-tools/resolve-order?type=order&value=xxx&warehouseId=CFF1
     * Quick lookup on AAD for pick_container + pick_detail to support order actions.
     */
    @GetMapping("/resolve-order")
    public ResponseEntity<?> resolveOrder(
            @RequestParam String type,
            @RequestParam String value,
            @RequestParam(required = false, defaultValue = "") String warehouseId) {
        logger.info("GET /api/test-tools/resolve-order?type={}&value={}&warehouseId={}", type, value, warehouseId);

        if (value == null || value.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Search value is required"));
        }

        try {
            Map<String, Object> result = orderActionService.resolveOrder(type.trim(), value.trim(), warehouseId.trim());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error resolving order", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/test-tools/setup-order
     * Insert t_hu_master + t_stored_item to prepare order for shipping/floor deny/short ship.
     */
    @PostMapping("/setup-order")
    public ResponseEntity<?> setupOrder(@RequestBody Map<String, String> request) {
        String warehouseId = request.get("warehouseId");
        String orderNumber = request.get("orderNumber");
        String setupType = request.getOrDefault("setupType", "normal");
        String containerId = request.get("containerId");
        String itemOverride = request.get("itemOverride");
        String qtyStr = request.get("quantityOverride");
        Integer quantityOverride = null;
        if (qtyStr != null && !qtyStr.trim().isEmpty()) {
            try { quantityOverride = Integer.parseInt(qtyStr.trim()); } catch (NumberFormatException ignored) {}
        }

        logger.info("POST /api/test-tools/setup-order wh={}, order={}, type={}, item={}, qty={}",
            warehouseId, orderNumber, setupType, itemOverride, quantityOverride);

        if (warehouseId == null || warehouseId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "warehouseId is required"));
        }
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "orderNumber is required"));
        }

        try {
            Map<String, Object> result = orderActionService.setupOrderData(
                warehouseId.trim(), orderNumber.trim(), setupType, containerId, itemOverride, quantityOverride);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error setting up order", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/test-tools/fulfillment-event
     * Send a fulfillment status event (Wizmo) for a container.
     */
    @PostMapping("/fulfillment-event")
    public ResponseEntity<?> sendFulfillmentEvent(@RequestBody Map<String, String> request) {
        String warehouseId = request.get("warehouseId");
        String containerId = request.get("containerId");
        String statusCode = request.get("statusCode");

        logger.info("POST /api/test-tools/fulfillment-event wh={}, container={}, status={}", warehouseId, containerId, statusCode);

        if (warehouseId == null || warehouseId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "warehouseId is required"));
        }
        if (containerId == null || containerId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "containerId is required"));
        }
        if (statusCode == null || statusCode.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "statusCode is required"));
        }

        try {
            Map<String, Object> result = orderActionService.sendFulfillmentEvent(warehouseId.trim(), containerId.trim(), statusCode.trim());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error sending fulfillment event", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/test-tools/item-lookup?itemNumber=xxx&warehouseId=CFF1
     * Look up an item in t_item_master. warehouseId is optional (searches all if blank).
     */
    @GetMapping("/item-lookup")
    public ResponseEntity<?> lookupItem(
            @RequestParam String itemNumber,
            @RequestParam(required = false, defaultValue = "") String warehouseId) {
        logger.info("GET /api/test-tools/item-lookup?itemNumber={}&warehouseId={}", itemNumber, warehouseId);

        if (itemNumber == null || itemNumber.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "itemNumber is required"));
        }

        try {
            Map<String, Object> result = itemImportService.lookupItem(itemNumber.trim(), warehouseId.trim());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error looking up item", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/test-tools/item-import
     * Import an item via XML gateway for one or more warehouses.
     * Body: { "itemNumber": "xxx", "warehouses": ["CFF1","AVP1"], "description": "...",
     *         "weight": "6.9", "length": "14.25", "width": "9.75", "height": "3.25", ... }
     */
    @PostMapping("/item-import")
    public ResponseEntity<?> importItem(@RequestBody Map<String, Object> request) {
        String itemNumber = request.get("itemNumber") != null ? request.get("itemNumber").toString() : null;
        logger.info("POST /api/test-tools/item-import item={}", itemNumber);

        if (itemNumber == null || itemNumber.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "itemNumber is required"));
        }

        try {
            Map<String, Object> result = itemImportService.importItem(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error importing item", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Failed to import item");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
