package com.system.complaints.controller;

import com.system.complaints.service.VisitPlanAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/visit-plan-access")
public class VisitPlanAccessController {
    private final VisitPlanAccessService accessService;

    public VisitPlanAccessController(VisitPlanAccessService accessService) {
        this.accessService = accessService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getUsers() {
        return ResponseEntity.ok(accessService.getAccessUsers());
    }

    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> setAccess(
            @PathVariable Long userId,
            @RequestParam boolean enabled
    ) {
        return ResponseEntity.ok(accessService.setAccess(userId, enabled));
    }
}
