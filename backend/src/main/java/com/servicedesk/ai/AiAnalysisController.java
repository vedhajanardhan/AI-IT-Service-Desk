package com.servicedesk.ai;

import com.servicedesk.ai.dto.AiAnalysisResponse;
import com.servicedesk.common.ApiResponse;
import com.servicedesk.user.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/ai-analysis")
@RequiredArgsConstructor
@Tag(name = "AI Analysis", description = "Trigger and inspect AI-driven incident analysis")
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    @PostMapping
    public ResponseEntity<ApiResponse<AiAnalysisResponse>> analyze(
            @PathVariable UUID incidentId, @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(aiAnalysisService.analyzeIncident(incidentId, currentUser)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AiAnalysisResponse>>> history(
            @PathVariable UUID incidentId, @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(aiAnalysisService.getAnalysisHistory(incidentId, currentUser)));
    }

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<AiAnalysisResponse>> latest(
            @PathVariable UUID incidentId, @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(aiAnalysisService.getLatestSuccessfulAnalysis(incidentId, currentUser)));
    }
}
