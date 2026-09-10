package com.servicedesk.knowledgebase.dto;

import com.servicedesk.incident.IncidentCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateArticleRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content,
        @NotBlank @Size(max = 500) String summary,
        IncidentCategory category,
        String tags
) {}
