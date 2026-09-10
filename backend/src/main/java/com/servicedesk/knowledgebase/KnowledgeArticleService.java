package com.servicedesk.knowledgebase;

import com.servicedesk.common.PagedResponse;
import com.servicedesk.common.exception.ResourceNotFoundException;
import com.servicedesk.incident.IncidentCategory;
import com.servicedesk.knowledgebase.dto.*;
import com.servicedesk.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeArticleService {

    private final KnowledgeArticleRepository repository;

    @Transactional
    @CacheEvict(value = "knowledgeBaseArticles", allEntries = true)
    public ArticleResponse create(CreateArticleRequest request, User author) {
        KnowledgeArticle article = KnowledgeArticle.builder()
                .title(request.title())
                .content(request.content())
                .summary(request.summary())
                .category(request.category())
                .tags(normalizeTags(request.tags()))
                .status(ArticleStatus.DRAFT)
                .author(author)
                .build();
        return toResponse(repository.save(article));
    }

    @Transactional
    @CacheEvict(value = "knowledgeBaseArticles", allEntries = true)
    public ArticleResponse update(UUID id, UpdateArticleRequest request) {
        KnowledgeArticle article = findOrThrow(id);
        article.setTitle(request.title());
        article.setContent(request.content());
        article.setSummary(request.summary());
        article.setCategory(request.category());
        article.setTags(normalizeTags(request.tags()));
        return toResponse(repository.save(article));
    }

    @Transactional
    @CacheEvict(value = "knowledgeBaseArticles", allEntries = true)
    public ArticleResponse publish(UUID id) {
        KnowledgeArticle article = findOrThrow(id);
        article.setStatus(ArticleStatus.PUBLISHED);
        return toResponse(repository.save(article));
    }

    @Transactional
    @CacheEvict(value = "knowledgeBaseArticles", allEntries = true)
    public ArticleResponse unpublish(UUID id) {
        KnowledgeArticle article = findOrThrow(id);
        article.setStatus(ArticleStatus.DRAFT);
        return toResponse(repository.save(article));
    }

    @Transactional
    @CacheEvict(value = "knowledgeBaseArticles", allEntries = true)
    public ArticleResponse archive(UUID id) {
        KnowledgeArticle article = findOrThrow(id);
        article.setStatus(ArticleStatus.ARCHIVED);
        return toResponse(repository.save(article));
    }

    @Transactional(readOnly = true)
    public ArticleResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    /**
     * Published-article search is the read-heavy, slow-changing path this
     * system actually caches (see RedisConfig) - full result pages are
     * cached by their filter combination for a short TTL.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "knowledgeBaseArticles",
            key = "'search:' + #status + ':' + #category + ':' + #keyword + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public PagedResponse<ArticleSummaryResponse> search(ArticleStatus status, IncidentCategory category,
                                                         String keyword, Pageable pageable) {
        Specification<KnowledgeArticle> spec = Specification.where(KnowledgeArticleSpecifications.hasStatus(status))
                .and(KnowledgeArticleSpecifications.hasCategory(category))
                .and(KnowledgeArticleSpecifications.keywordMatches(keyword));

        Page<KnowledgeArticle> page = repository.findAll(spec, pageable);
        return PagedResponse.from(page.map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public List<ArticleSummaryResponse> findRelevantByTags(List<String> tags, int limit) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        Specification<KnowledgeArticle> spec = Specification
                .where(KnowledgeArticleSpecifications.hasStatus(ArticleStatus.PUBLISHED));
        Specification<KnowledgeArticle> tagSpec = null;
        for (String tag : tags) {
            Specification<KnowledgeArticle> single = KnowledgeArticleSpecifications.hasTag(tag);
            tagSpec = tagSpec == null ? single : tagSpec.or(single);
        }
        if (tagSpec != null) {
            spec = spec.and(tagSpec);
        }
        return repository.findAll(spec).stream().limit(limit).map(this::toSummary).toList();
    }

    private KnowledgeArticle findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge article not found: " + id));
    }

    private String normalizeTags(String rawTags) {
        if (rawTags == null || rawTags.isBlank()) {
            return null;
        }
        return String.join(",", Arrays.stream(rawTags.split(","))
                .map(String::trim).map(String::toLowerCase).filter(s -> !s.isEmpty()).toList());
    }

    private List<String> parseTags(String tags) {
        return tags == null || tags.isBlank() ? List.of() : List.of(tags.split(","));
    }

    private ArticleResponse toResponse(KnowledgeArticle a) {
        return new ArticleResponse(a.getId(), a.getTitle(), a.getContent(), a.getSummary(), a.getCategory(),
                parseTags(a.getTags()), a.getStatus().name(), a.getAuthor().getFullName(),
                a.getCreatedAt(), a.getUpdatedAt());
    }

    private ArticleSummaryResponse toSummary(KnowledgeArticle a) {
        return new ArticleSummaryResponse(a.getId(), a.getTitle(), a.getSummary(), a.getCategory(),
                parseTags(a.getTags()), a.getStatus().name(), a.getUpdatedAt());
    }
}
