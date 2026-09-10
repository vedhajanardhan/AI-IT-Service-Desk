package com.servicedesk.knowledgebase;

import com.servicedesk.incident.IncidentCategory;
import org.springframework.data.jpa.domain.Specification;

public final class KnowledgeArticleSpecifications {

    private KnowledgeArticleSpecifications() {}

    public static Specification<KnowledgeArticle> hasStatus(ArticleStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<KnowledgeArticle> hasCategory(IncidentCategory category) {
        return (root, query, cb) -> category == null ? null : cb.equal(root.get("category"), category);
    }

    public static Specification<KnowledgeArticle> keywordMatches(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            String like = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("summary")), like),
                    cb.like(cb.lower(root.get("tags")), like)
            );
        };
    }

    public static Specification<KnowledgeArticle> hasTag(String tag) {
        return (root, query, cb) -> tag == null || tag.isBlank()
                ? null : cb.like(cb.lower(root.get("tags")), "%" + tag.toLowerCase() + "%");
    }
}
