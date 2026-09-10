package com.servicedesk.ai.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Selects mock vs. real AI provider at runtime based on {@code app.ai.provider}.
 * Kept as a simple factory rather than Spring profiles/@Conditional so the
 * choice is visible in one place and easy to explain: "mock" needs zero
 * configuration and is the default so the whole app runs locally with no
 * API key; "anthropic" requires ANTHROPIC_API_KEY.
 */
@Component
public class AiProviderFactory {

    private final MockAiProvider mockAiProvider;
    private final AnthropicAiProvider anthropicAiProvider;
    private final String activeProviderName;

    public AiProviderFactory(
            MockAiProvider mockAiProvider,
            AnthropicAiProvider anthropicAiProvider,
            @Value("${app.ai.provider}") String activeProviderName) {
        this.mockAiProvider = mockAiProvider;
        this.anthropicAiProvider = anthropicAiProvider;
        this.activeProviderName = activeProviderName;
    }

    public AiProvider getActiveProvider() {
        return "anthropic".equalsIgnoreCase(activeProviderName) ? anthropicAiProvider : mockAiProvider;
    }
}
