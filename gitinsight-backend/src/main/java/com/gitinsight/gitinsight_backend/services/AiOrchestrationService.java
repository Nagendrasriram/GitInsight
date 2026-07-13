package com.gitinsight.gitinsight_backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AiOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AiOrchestrationService.class);

    @Value("${openrouter.api.keys}")
    private List<String> openRouterKeys;

    @Value("${openrouter.base-url:https://openrouter.ai/api/v1}")
    private String openRouterBaseUrl;

    @Value("${openrouter.model:openai/gpt-4o-mini}")
    private String openRouterModel;

    private final GoogleGenAiChatModel googleGenAiChatModel;
    private final MistralAiChatModel mistralAiChatModel;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    public AiOrchestrationService(GoogleGenAiChatModel googleGenAiChatModel,
                                  MistralAiChatModel mistralAiChatModel) {
        this.googleGenAiChatModel = googleGenAiChatModel;
        this.mistralAiChatModel = mistralAiChatModel;
    }
    public String generateAnalysis(String systemPrompt, String userCode) {
        String combinedPrompt = systemPrompt + "\n\nSource Code to Analyze:\n" + userCode;

        // ── TIER 1: OpenRouter ──────────────────────────────────────────
        if (openRouterKeys != null && !openRouterKeys.isEmpty()) {
            int totalKeys  = openRouterKeys.size();
            int startIndex = roundRobinIndex.get() % totalKeys;

            for (int attempt = 0; attempt < totalKeys; attempt++) {
                int    keyIndex = (startIndex + attempt) % totalKeys;
                String key      = openRouterKeys.get(keyIndex).trim();

                log.info("🤖 [OpenRouter] Attempt {}/{} — key index {}", attempt + 1, totalKeys, keyIndex);

                try {
                    String result = callOpenRouterWithKey(key, combinedPrompt);
                    roundRobinIndex.set((keyIndex + 1) % totalKeys);
                    log.info("✅ [OpenRouter] Success on key index {}", keyIndex);
                    return result;
                } catch (Exception e) {
                    log.warn("⚠️ [OpenRouter] Key index {} failed: {}", keyIndex, e.getMessage());
                }
            }
            log.warn("🔄 All OpenRouter keys exhausted — escalating to Tier 2 (Gemini).");
        }

        // ── TIER 2: Gemini ──────────────────────────────────────────────
        log.info("🧠 [Gemini] Initiating Tier 2 analysis...");
        try {
            String result = googleGenAiChatModel.call(combinedPrompt);
            log.info("✅ [Gemini] Analysis completed successfully.");
            return result;
        } catch (Exception e) {
            log.warn("⚠️ [Gemini] Failed: {} — escalating to Tier 3 (Mistral).", e.getMessage());
        }

        // ── TIER 3: Mistral ─────────────────────────────────────────────
        log.info("🌀 [Mistral] Initiating Tier 3 (last-resort) analysis...");
        try {
            String result = mistralAiChatModel.call(combinedPrompt);
            log.info("✅ [Mistral] Analysis completed successfully.");
            return result;
        } catch (Exception e) {
            throw new RuntimeException("❌ All AI providers failed.", e);
        }
    }
//    public String generateAnalysis(String systemPrompt, String userCode) {
//        String combinedPrompt = systemPrompt + "\n\nSource Code to Analyze:\n" + userCode;
//
//        if (openRouterKeys != null && !openRouterKeys.isEmpty()) {
//            int totalKeys = openRouterKeys.size();
//            int startIndex = roundRobinIndex.get() % totalKeys;
//
//            for (int attempt = 0; attempt < totalKeys; attempt++) {
//                int keyIndex = (startIndex + attempt) % totalKeys;
//                String key = openRouterKeys.get(keyIndex).trim();
//                try {
//                    String result = callOpenRouterWithKey(key, combinedPrompt);
//                    roundRobinIndex.set((keyIndex + 1) % totalKeys);
//                    return result;
//                } catch (Exception e) {
//                    log.warn("⚠️ OpenRouter key {} failed: {}", keyIndex, e.getMessage());
//                }
//            }
//        }
//
//        try {
//            return googleGenAiChatModel.call(combinedPrompt);
//        } catch (Exception e) {
//            log.warn("⚠️ Gemini failed: {}", e.getMessage());
//        }
//
//        try {
//            return mistralAiChatModel.call(combinedPrompt);
//        } catch (Exception e) {
//            throw new RuntimeException("❌ All AI providers failed.", e);
//        }
//    }

    private String callOpenRouterWithKey(String key, String prompt) throws Exception {

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(openRouterBaseUrl)
                .apiKey(key)              // ← passes String, builder handles ApiKey wrapping
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(openRouterModel)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();

        return chatModel.call(prompt);
    }
}

//package com.gitinsight.gitinsight_backend.services;
//
//import org.springframework.ai.google.genai.GoogleGenAiChatModel;
//import org.springframework.ai.openai.OpenAiChatModel;
//import org.springframework.stereotype.Service;
//
//@Service
//public class AiOrchestrationService {
//
//    private final OpenAiChatModel openAiChatModel;
//    private final GoogleGenAiChatModel googleGenAiChatModel;
//
//    // Spring will automatically inject both configured AI brains here
//    public AiOrchestrationService(OpenAiChatModel openAiChatModel, GoogleGenAiChatModel googleGenAiChatModel) {
//        this.openAiChatModel = openAiChatModel;
//        this.googleGenAiChatModel = googleGenAiChatModel;
//    }
//
//    /**
//     * Executes a prompt using the Primary model (OpenRouter),
//     * with an automatic hot-swap to the Fallback model (Native Google Gemini).
//     */
//    public String generateAnalysis(String systemPrompt, String userCode) {
//        String combinedPrompt = systemPrompt + "\n\nSource Code to Analyze:\n" + userCode;
//
//        try {
//            System.out.println("🤖 Attempting primary analysis via OpenRouter...");
//            return openAiChatModel.call(combinedPrompt);
//
//        } catch (Exception e) {
//            System.err.println("⚠️ OpenRouter failed or hit rate limits: " + e.getMessage());
//            System.out.println("🔄 Initiating automatic high-availability failover to Native Google Gemini...");
//
//            try {
//                return googleGenAiChatModel.call(combinedPrompt);
//            } catch (Exception fallbackException) {
//                System.err.println("❌ Critical Error: Both AI providers are currently unavailable.");
//                throw new RuntimeException("AI Core completely offline. Details: " + fallbackException.getMessage(), fallbackException);
//            }
//        }
//    }
//}

//package com.gitinsight.gitinsight_backend.services;
//import org.springframework.ai.google.genai.GoogleGenAiChatModel;
//import org.springframework.ai.openai.OpenAiChatModel;
//import org.springframework.stereotype.Service;
//
//@Service
//public class AiOrchestrationService {
//
//    private final OpenAiChatModel openAiChatModel;
//    private final GoogleGenAiChatModel googleGenAiChatModel;
//
//    // Spring will automatically inject both configured AI brains here
//    public AiOrchestrationService(OpenAiChatModel openAiChatModel, GoogleGenAiChatModel googleGenAiChatModel) {
//        this.openAiChatModel = openAiChatModel;
//        this.googleGenAiChatModel = googleGenAiChatModel;
//    }
//
//    /**
//     * Executes a prompt using the Primary model (OpenRouter),
//     * with an automatic hot-swap to the Fallback model (Native Google Gemini).
//     */
//    public String generateAnalysis(String systemPrompt, String userCode) {
//        String combinedPrompt = systemPrompt + "\n\nSource Code to Analyze:\n" + userCode;
//
//        try {
//            System.out.println("🤖 Attempting primary analysis via OpenRouter...");
//            return openAiChatModel.call(combinedPrompt);
//
//        } catch (Exception e) {
//            System.err.println("⚠️ OpenRouter failed or hit rate limits: " + e.getMessage());
//            System.out.println("🔄 Initiating automatic high-availability failover to Native Google Gemini...");
//
//            try {
//                return googleGenAiChatModel.call(combinedPrompt);
//            } catch (Exception fallbackException) {
//                System.err.println("❌ Critical Error: Both AI providers are currently unavailable.");
//                throw new RuntimeException("AI Core completely offline. Details: " + fallbackException.getMessage(), fallbackException);
//            }
//        }
//    }
//}

