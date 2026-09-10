package com.servicedesk.ai.provider;

import com.servicedesk.ai.dto.AiAnalysisRequest;
import com.servicedesk.ai.dto.AiAnalysisResult;
import com.servicedesk.common.exception.AiProviderException;

/**
 * Every AI backend (mock, Anthropic, or anything added later) implements
 * this. Nothing outside the `ai` package should ever know or care which
 * provider is active - see {@link AiProviderFactory}.
 */
public interface AiProvider {

    /**
     * @throws AiProviderException on timeout, transport failure, or a
     *         response that fails schema validation. Callers must treat
     *         this as recoverable - AI failure must never break normal
     *         incident management.
     */
    AiAnalysisResult analyze(AiAnalysisRequest request);

    String providerName();
}
