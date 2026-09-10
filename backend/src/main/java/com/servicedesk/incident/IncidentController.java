package com.servicedesk.incident;

import com.servicedesk.common.ApiResponse;
import com.servicedesk.common.PagedResponse;
import com.servicedesk.incident.dto.*;
import com.servicedesk.user.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
@Tag(name = "Incidents", description = "Incident lifecycle: create, triage, assign, resolve, escalate")
public class IncidentController {

    private final IncidentService incidentService;

    @PostMapping
    public ResponseEntity<ApiResponse<IncidentResponse>> create(
            @Valid @RequestBody CreateIncidentRequest request,
            @AuthenticationPrincipal User currentUser) {
        var response = incidentService.createIncident(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, "Incident created"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IncidentResponse>> get(
            @PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(incidentService.getIncident(id, currentUser)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<IncidentSummaryResponse>>> search(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentCategory category,
            @RequestParam(required = false) IncidentSeverity severity,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal User currentUser,
            @PageableDefault(size = 20) Pageable pageable) {
        var response = incidentService.searchIncidents(status, category, severity, keyword, currentUser, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<IncidentResponse>> assign(
            @PathVariable UUID id, @Valid @RequestBody AssignIncidentRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(incidentService.assignIncident(id, request, currentUser)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<IncidentResponse>> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(incidentService.changeStatus(id, request, currentUser)));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<IncidentResponse>> resolve(
            @PathVariable UUID id, @Valid @RequestBody ResolveIncidentRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(incidentService.resolveIncident(id, request, currentUser)));
    }

    @PostMapping("/{id}/escalate")
    public ResponseEntity<ApiResponse<IncidentResponse>> escalate(
            @PathVariable UUID id, @Valid @RequestBody EscalateIncidentRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(incidentService.escalateIncident(id, request, currentUser)));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<ApiResponse<IncidentResponse>> reopen(
            @PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(incidentService.reopenIncident(id, currentUser)));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable UUID id, @Valid @RequestBody AddCommentRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(incidentService.addComment(id, request, currentUser)));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getComments(
            @PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(incidentService.getComments(id, currentUser)));
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<ApiResponse<List<IncidentEventResponse>>> timeline(
            @PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(incidentService.getTimeline(id, currentUser)));
    }
}
