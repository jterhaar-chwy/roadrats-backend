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
@RequestMapping("/api/wms360")
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

    private String normalizeEnv(String env) {
        if (env == null) return "test";
        String e = env.trim().toLowerCase();
        return "prod".equals(e) ? "prod" : "test";
    }

    @GetMapping("/lookup")
    public ResponseEntity<?> lookupOrder(
            @RequestParam String type,
            @RequestParam String value,
            @RequestParam(required = false, defaultValue = "") String warehouseId,
            @RequestParam(required = false, defaultValue = "both") String stack,
            @RequestParam(required = false, defaultValue = "test") String env) {
        String nEnv = normalizeEnv(env);
        logger.info("GET /api/wms360/lookup?type={}&value={}&warehouseId={}&stack={}&env={}", type, value, warehouseId, stack, nEnv);

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
            Map<String, Object> result = orderLookupService.lookupOrder(type.trim(), value.trim(), warehouseId.trim(), normalizedStack, nEnv);
            return ResponseEntity.ok(result);
        } catch (Exception e2) {
            logger.error("Error during order lookup", e2);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Failed to perform lookup");
            error.put("message", e2.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * POST /api/wms360/lookup/v2/resolve
     * Body: omsOrderNumber?, orderNumber?, containerId?, warehouseId?, stack (aad|io|both), env
     * Returns all distinct (warehouseId, orderNumber, containerId, omsOrderNumber) rows found.
     */
    @PostMapping("/lookup/v2/resolve")
    public ResponseEntity<?> resolveLookupKeys(@RequestBody Map<String, String> body) {
        String nEnv = normalizeEnv(body != null ? body.get("env") : null);
        String stack = body != null && body.get("stack") != null ? body.get("stack").trim().toLowerCase() : "both";
        if (!List.of("aad", "io", "both").contains(stack)) {
            stack = "both";
        }
        logger.info("POST /api/wms360/lookup/v2/resolve stack={} env={}", stack, nEnv);

        try {
            Map<String, Object> result = orderLookupService.resolveOrderKeys(
                body != null ? body.get("omsOrderNumber") : null,
                body != null ? body.get("orderNumber") : null,
                body != null ? body.get("containerId") : null,
                body != null ? body.get("warehouseId") : null,
                stack,
                nEnv);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error resolving lookup keys", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/wms360/lookup/v2/details
     * Body: warehouseId, orderNumber, containerId?, stack, env
     * Same data as classic lookup, plus "groups" bucketed for UI.
     */
    @PostMapping("/lookup/v2/details")
    public ResponseEntity<?> lookupGrouped(@RequestBody Map<String, String> body) {
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body required"));
        }
        String wh = body.get("warehouseId");
        String ord = body.get("orderNumber");
        String nEnv = normalizeEnv(body.get("env"));
        String stack = body.get("stack") != null ? body.get("stack").trim().toLowerCase() : "both";
        if (!List.of("aad", "io", "both").contains(stack)) {
            stack = "both";
        }
        logger.info("POST /api/wms360/lookup/v2/details wh={}, order={}, env={}", wh, ord, nEnv);

        if (wh == null || wh.trim().isEmpty() || ord == null || ord.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "warehouseId and orderNumber are required"));
        }

        try {
            Map<String, Object> result = orderLookupService.lookupOrderGrouped(
                wh.trim(),
                ord.trim(),
                body.get("containerId") != null ? body.get("containerId").trim() : null,
                stack,
                nEnv);
            if (result.containsKey("error")) {
                return ResponseEntity.badRequest().body(result);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error during grouped lookup", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/ship-order")
    public ResponseEntity<?> shipOrder(@RequestBody Map<String, String> request) {
        String warehouseId = request.get("warehouseId");
        String orderNumber = request.get("orderNumber");
        String nEnv = normalizeEnv(request.get("env"));

        logger.info("POST /api/wms360/ship-order wh={}, order={}, env={}", warehouseId, orderNumber, nEnv);

        if (warehouseId == null || warehouseId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "warehouseId is required"));
        }
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "orderNumber is required"));
        }

        try {
            Map<String, Object> result = shipOrderService.shipOrder(warehouseId.trim(), orderNumber.trim(), nEnv);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error shipping order", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Failed to ship order");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/ship-container")
    public ResponseEntity<?> shipContainer(@RequestBody Map<String, String> request) {
        String warehouseId = request.get("warehouseId");
        String containerId = request.get("containerId");
        String nEnv = normalizeEnv(request.get("env"));

        logger.info("POST /api/wms360/ship-container wh={}, container={}, env={}", warehouseId, containerId, nEnv);

        if (warehouseId == null || warehouseId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "warehouseId is required"));
        }
        if (containerId == null || containerId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "containerId is required"));
        }

        try {
            Map<String, Object> result = shipOrderService.shipContainer(warehouseId.trim(), containerId.trim(), nEnv);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error shipping container", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Failed to ship container");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/connection-test")
    public ResponseEntity<?> testConnection(@RequestParam(required = false, defaultValue = "test") String env) {
        String nEnv = normalizeEnv(env);
        logger.info("GET /api/wms360/connection-test?env={}", nEnv);
        try {
            return ResponseEntity.ok(orderLookupService.testConnection(nEnv));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/resolve-order")
    public ResponseEntity<?> resolveOrder(
            @RequestParam String type,
            @RequestParam String value,
            @RequestParam(required = false, defaultValue = "") String warehouseId,
            @RequestParam(required = false, defaultValue = "test") String env) {
        String nEnv = normalizeEnv(env);
        logger.info("GET /api/wms360/resolve-order?type={}&value={}&warehouseId={}&env={}", type, value, warehouseId, nEnv);

        if (value == null || value.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Search value is required"));
        }

        try {
            Map<String, Object> result = orderActionService.resolveOrder(type.trim(), value.trim(), warehouseId.trim(), nEnv);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error resolving order", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/setup-order")
    public ResponseEntity<?> setupOrder(@RequestBody Map<String, String> request) {
        String warehouseId = request.get("warehouseId");
        String orderNumber = request.get("orderNumber");
        String setupType = request.getOrDefault("setupType", "normal");
        String containerId = request.get("containerId");
        String itemOverride = request.get("itemOverride");
        String qtyStr = request.get("quantityOverride");
        String nEnv = normalizeEnv(request.get("env"));
        Integer quantityOverride = null;
        if (qtyStr != null && !qtyStr.trim().isEmpty()) {
            try { quantityOverride = Integer.parseInt(qtyStr.trim()); } catch (NumberFormatException ignored) {}
        }

        logger.info("POST /api/wms360/setup-order wh={}, order={}, type={}, item={}, qty={}, env={}",
            warehouseId, orderNumber, setupType, itemOverride, quantityOverride, nEnv);

        if (warehouseId == null || warehouseId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "warehouseId is required"));
        }
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "orderNumber is required"));
        }

        try {
            Map<String, Object> result = orderActionService.setupOrderData(
                warehouseId.trim(), orderNumber.trim(), setupType, containerId, itemOverride, quantityOverride, nEnv);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error setting up order", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/fulfillment-event")
    public ResponseEntity<?> sendFulfillmentEvent(@RequestBody Map<String, String> request) {
        String warehouseId = request.get("warehouseId");
        String containerId = request.get("containerId");
        String statusCode = request.get("statusCode");
        String nEnv = normalizeEnv(request.get("env"));

        logger.info("POST /api/wms360/fulfillment-event wh={}, container={}, status={}, env={}", warehouseId, containerId, statusCode, nEnv);

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
            Map<String, Object> result = orderActionService.sendFulfillmentEvent(warehouseId.trim(), containerId.trim(), statusCode.trim(), nEnv);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error sending fulfillment event", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/item-lookup")
    public ResponseEntity<?> lookupItem(
            @RequestParam String itemNumber,
            @RequestParam(required = false, defaultValue = "") String warehouseId,
            @RequestParam(required = false, defaultValue = "test") String env) {
        String nEnv = normalizeEnv(env);
        logger.info("GET /api/wms360/item-lookup?itemNumber={}&warehouseId={}&env={}", itemNumber, warehouseId, nEnv);

        if (itemNumber == null || itemNumber.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "itemNumber is required"));
        }

        try {
            Map<String, Object> result = itemImportService.lookupItem(itemNumber.trim(), warehouseId.trim(), nEnv);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error looking up item", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/item-import")
    public ResponseEntity<?> importItem(@RequestBody Map<String, Object> request) {
        String itemNumber = request.get("itemNumber") != null ? request.get("itemNumber").toString() : null;
        String nEnv = normalizeEnv(request.get("env") != null ? request.get("env").toString() : "test");
        logger.info("POST /api/wms360/item-import item={}, env={}", itemNumber, nEnv);

        if (itemNumber == null || itemNumber.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "itemNumber is required"));
        }

        try {
            Map<String, Object> result = itemImportService.importItem(request, nEnv);
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
