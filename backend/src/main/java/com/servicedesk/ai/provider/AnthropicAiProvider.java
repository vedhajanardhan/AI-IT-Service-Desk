package com.servicedesk.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.ai.dto.AiAnalysisRequest;
import com.servicedesk.ai.dto.AiAnalysisResult;
import com.servicedesk.common.exception.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calls the Anthropic Messages API and demands a strict JSON response
 * matching {@link AiAnalysisResult}'s shape. This is intentionally never
 * allowed to execute anything - it only classifies and recommends; the
 * remediation engine independently re-validates any recommended action
 * code against the enabled action list before anything runs.
 */
@Component
public class AnthropicAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicAiProvider.class);

    private static final Set<String> VALID_ACTION_CODES = Set.of(
            "RESTART_APPLICATION_SERVICE", "CLEAR_APPLICATION_CACHE", "INVALIDATE_REDIS_CACHE",
            "RESTART_CONNECTION_POOL", "RUN_HEALTH_CHECK", "RETRY_FAILED_WORKFLOW");

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String model;
    private final long timeoutMs;
    private final long maxRetries;

    public AnthropicAiProvider(
            WebClient.Builder webClientBuilder,
            @Value("${app.ai.anthropic.base-url}") String baseUrl,
            @Value("${app.ai.anthropic.api-key}") String apiKey,
            @Value("${app.ai.anthropic.model}") String model,
            @Value("${app.ai.anthropic.timeout-ms}") long timeoutMs,
            @Value("${app.ai.anthropic.max-retries}") long maxRetries) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
        this.maxRetries = maxRetries;
    }

    @Override
    public AiAnalysisResult analyze(AiAnalysisRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiProviderException(
                    "ANTHROPIC_API_KEY is not configured; set app.ai.provider=mock or provide a real key");
        }

        String prompt = buildPrompt(request);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 1024);
        body.put("messages", List.of(message));

        try {
            String rawResponse = webClient.post()
                    .uri("")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(500))
                            .filter(this::isRetryable))
                    .block();

            return parseAndValidate(rawResponse);
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Anthropic AI provider call failed: {}", e.getMessage());
            throw new AiProviderException("AI analysis failed: " + e.getMessage(), e);
        }
    }

    private boolean isRetryable(Throwable throwable) {
        // Retry on timeouts and transport-level failures; don't retry on
        // responses we already recognize as malformed/invalid.
        return !(throwable instanceof AiProviderException);
    }

    private String buildPrompt(AiAnalysisRequest request) {
        return """
                You are an IT incident triage assistant. Analyze this incident and respond with
                ONLY a single JSON object (no markdown, no commentary, no code fences) with exactly
                these fields:

                {
                  "classification": one of [APPLICATION_ERROR, DATABASE, NETWORK, AUTHENTICATION, PERFORMANCE, INFRASTRUCTURE, SECURITY, OTHER],
                  "severityRecommendation": one of [LOW, MEDIUM, HIGH, CRITICAL],
                  "priorityRecommendation": one of [P4_LOW, P3_MEDIUM, P2_HIGH, P1_URGENT],
                  "rootCause": a short probable root cause (1-2 sentences),
                  "confidenceScore": a number between 0.0 and 1.0,
                  "recommendedActionCode": one of [RESTART_APPLICATION_SERVICE, CLEAR_APPLICATION_CACHE, INVALIDATE_REDIS_CACHE, RESTART_CONNECTION_POOL, RUN_HEALTH_CHECK, RETRY_FAILED_WORKFLOW] or null if no safe automated action applies,
                  "explanation": a short human-readable explanation of your reasoning,
                  "relevantKnowledgeTags": an array of short lowercase-hyphenated tags (e.g. ["database-connection-problems"])
                }

                Incident title: %s
                Incident description: %s
                Reported category: %s
                Reported severity: %s
                """.formatted(request.title(), request.description(), request.category(), request.severity());
    }

    private AiAnalysisResult parseAndValidate(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode contentArray = root.path("content");
            if (!contentArray.isArray() || contentArray.isEmpty()) {
                throw new AiProviderException("Malformed AI response: no content blocks returned");
            }

            String text = null;
            for (JsonNode block : contentArray) {
                if ("text".equals(block.path("type").asText())) {
                    text = block.path("text").asText();
                    break;
                }
            }
            if (text == null) {
                throw new AiProviderException("Malformed AI response: no text content block found");
            }

            String jsonPayload = extractJsonObject(text);
            JsonNode analysis = objectMapper.readTree(jsonPayload);

            String classification = requireText(analysis, "classification");
            String severityRecommendation = requireText(analysis, "severityRecommendation");
            String priorityRecommendation = requireText(analysis, "priorityRecommendation");
            String rootCause = requireText(analysis, "rootCause");
            double confidence = analysis.path("confidenceScore").asDouble(-1);
            String explanation = requireText(analysis, "explanation");
            String actionCode = analysis.path("recommendedActionCode").isNull()
                    ? null : analysis.path("recommendedActionCode").asText(null);

            if (confidence < 0.0 || confidence > 1.0) {
                throw new AiProviderException("Malformed AI response: confidenceScore out of range: " + confidence);
            }
            if (actionCode != null && !VALID_ACTION_CODES.contains(actionCode)) {
                log.warn("AI recommended unknown action code '{}'; dropping recommendation", actionCode);
                actionCode = null;
            }

            List<String> tags = new ArrayList<>();
            analysis.path("relevantKnowledgeTags").forEach(t -> tags.add(t.asText()));

            return new AiAnalysisResult(classification, severityRecommendation, priorityRecommendation,
                    rootCause, confidence, actionCode, explanation, tags);

        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Malformed or unparseable AI response: " + e.getMessage(), e);
        }
    }

    private String requireText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new AiProviderException("Malformed AI response: missing required field '" + field + "'");
        }
        return value.asText();
    }

    /** The model is instructed to return raw JSON, but defensively strip any stray fencing. */
    private String extractJsonObject(String text) {
        String trimmed = text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) {
            throw new AiProviderException("Malformed AI response: no JSON object found in model output");
        }
        return trimmed.substring(start, end + 1);
    }

    @Override
    public String providerName() {
        return "anthropic";
    }
}
