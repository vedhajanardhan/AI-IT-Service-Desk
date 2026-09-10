package com.servicedesk.remediation;

import com.servicedesk.common.ApiResponse;
import com.servicedesk.remediation.dto.*;
import com.servicedesk.user.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Remediation", description = "Safe auto-remediation: recommend, approve, execute, track")
public class RemediationController {

    private final RemediationService remediationService;

    @GetMapping("/api/v1/remediation-actions")
    public ResponseEntity<ApiResponse<List<RemediationActionResponse>>> listActions() {
        return ResponseEntity.ok(ApiResponse.ok(remediationService.listActions()));
    }

    @PostMapping("/api/v1/incidents/{incidentId}/remediation/request")
    public ResponseEntity<ApiResponse<RemediationExecutionResponse>> request(
            @PathVariable UUID incidentId, @Valid @RequestBody RequestRemediationRequest request,
            @AuthenticationPrincipal User currentUser) {
        var response = remediationService.requestRemediation(incidentId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PostMapping("/api/v1/remediation-executions/{executionId}/approve")
    public ResponseEntity<ApiResponse<RemediationExecutionResponse>> approve(
            @PathVariable UUID executionId, @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(remediationService.approveRemediation(executionId, currentUser)));
    }

    @PostMapping("/api/v1/remediation-executions/{executionId}/reject")
    public ResponseEntity<ApiResponse<RemediationExecutionResponse>> reject(
            @PathVariable UUID executionId, @Valid @RequestBody RejectRemediationRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(remediationService.rejectRemediation(executionId, request, currentUser)));
    }

    @GetMapping("/api/v1/incidents/{incidentId}/remediation/history")
    public ResponseEntity<ApiResponse<List<RemediationExecutionResponse>>> history(@PathVariable UUID incidentId) {
        return ResponseEntity.ok(ApiResponse.ok(remediationService.getExecutionHistory(incidentId)));
    }
}
