package com.servicedesk.knowledgebase;

import com.servicedesk.ai.AiAnalysisService;
import com.servicedesk.ai.dto.AiAnalysisResponse;
import com.servicedesk.common.ApiResponse;
import com.servicedesk.knowledgebase.dto.ArticleSummaryResponse;
import com.servicedesk.user.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Implements the "Knowledge Recommendation" step of the incident workflow:
 * takes the tags from the most recent successful AI analysis and looks up
 * matching published knowledge base articles. Kept as its own thin
 * controller rather than folded into AiAnalysisController or
 * KnowledgeArticleController, since it genuinely depends on both.
 *
 * Reuses AiAnalysisService's ownership-checked overload (added in the
 * Phase 10 security review) rather than the unchecked one, so an employee
 * can't fish for another employee's AI analysis tags through this
 * back door either.
 */
@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/knowledge-recommendations")
@RequiredArgsConstructor
@Tag(name = "Knowledge Recommendations", description = "AI-analysis-driven knowledge article suggestions for an incident")
public class KnowledgeRecommendationController {

    private static final int MAX_RECOMMENDATIONS = 5;

    private final AiAnalysisService aiAnalysisService;
    private final KnowledgeArticleService knowledgeArticleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ArticleSummaryResponse>>> recommendationsFor(
            @PathVariable UUID incidentId, @AuthenticationPrincipal User currentUser) {
        AiAnalysisResponse analysis = aiAnalysisService.getLatestSuccessfulAnalysis(incidentId, currentUser);
        List<ArticleSummaryResponse> recommendations =
                knowledgeArticleService.findRelevantByTags(analysis.relevantKnowledgeTags(), MAX_RECOMMENDATIONS);
        return ResponseEntity.ok(ApiResponse.ok(recommendations));
    }
}
