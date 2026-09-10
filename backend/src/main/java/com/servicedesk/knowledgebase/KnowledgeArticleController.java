package com.servicedesk.knowledgebase;

import com.servicedesk.common.ApiResponse;
import com.servicedesk.common.PagedResponse;
import com.servicedesk.incident.IncidentCategory;
import com.servicedesk.knowledgebase.dto.*;
import com.servicedesk.user.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge-base")
@RequiredArgsConstructor
@Tag(name = "Knowledge Base", description = "Searchable IT knowledge articles")
public class KnowledgeArticleController {

    private final KnowledgeArticleService service;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ArticleResponse>> create(
            @Valid @RequestBody CreateArticleRequest request, @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(request, currentUser)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ArticleResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateArticleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, request)));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ArticleResponse>> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.publish(id)));
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ArticleResponse>> unpublish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.unpublish(id)));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ArticleResponse>> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.archive(id)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ArticleResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(id)));
    }

    /** Full search across all statuses - engineer/admin only (see SecurityConfig). */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ArticleSummaryResponse>>> search(
            @RequestParam(required = false) ArticleStatus status,
            @RequestParam(required = false) IncidentCategory category,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(service.search(status, category, keyword, pageable)));
    }

    /** Published-only search, open to anyone (self-service before filing a ticket). */
    @GetMapping("/public")
    public ResponseEntity<ApiResponse<PagedResponse<ArticleSummaryResponse>>> searchPublic(
            @RequestParam(required = false) IncidentCategory category,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(service.search(ArticleStatus.PUBLISHED, category, keyword, pageable)));
    }
}
