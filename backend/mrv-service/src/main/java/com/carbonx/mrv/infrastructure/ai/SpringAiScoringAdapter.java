package com.carbonx.mrv.infrastructure.ai;

import com.carbonx.mrv.application.port.out.SpringAiScoringPort;
import com.carbonx.mrv.application.support.FraudSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Spring AI {@link ChatClient} implementation of the scoring port.
 *
 * <ul>
 *   <li>{@code aiScoring} circuit breaker + bulkhead: any LLM error or timeout is answered from the
 *       deterministic DQ1..DQ8 weighted score carried in the request, so the CVA always gets a
 *       pre-score.</li>
 *   <li>{@code aiChat} circuit breaker + rate limiter: the assistant widget degrades to a clear
 *       message instead of blocking threads on an unhealthy provider.</li>
 * </ul>
 * The call runs on a bounded worker so a hung HTTP connection cannot outlive
 * {@code carbonx.ai.timeout-seconds}.
 */
@Component
@Slf4j
public class SpringAiScoringAdapter implements SpringAiScoringPort {

    static final String AI_VERSION = "spring-ai-openai";
    private static final int MAX_NOTES_LENGTH = 1_000;
    private static final int WORKER_THREADS = 4;
    private static final String UNAVAILABLE_MESSAGE =
            "Trợ lý CarbonX AI tạm thời không khả dụng. Vui lòng thử lại sau ít phút.";

    private static final String SCORING_SYSTEM_PROMPT = """
            Bạn là chuyên gia thẩm định MRV tín chỉ carbon theo phương pháp luận VM0038 cho đội CVA.
            Nhiệm vụ: đánh giá độ tin cậy của báo cáo phát thải dựa trên 8 luật dữ liệu DQ1..DQ8.
            Điểm càng cao càng đáng tin (0 = gian lận rõ ràng, 100 = dữ liệu sạch).
            Chỉ trả về JSON đúng định dạng, không markdown, không giải thích ngoài JSON:
            {"score": <số 0..100>, "notes": "<nhận xét ngắn gọn bằng tiếng Việt, tối đa 400 ký tự>"}
            """;

    private static final String CHAT_SYSTEM_PROMPT = """
            Bạn là CarbonX Carbon Assistant, trợ lý cho sàn giao dịch tín chỉ carbon xe điện.
            Hãy trả lời ngắn gọn, đúng trọng tâm về MRV, VM0038, tín chỉ carbon và quy trình CVA/Admin.
            Không bịa số liệu của doanh nghiệp; nếu thiếu dữ liệu hãy nói rõ cần bổ sung gì.
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;
    private final ExecutorService executor;
    private volatile ChatClient chatClient;

    public SpringAiScoringAdapter(ObjectProvider<ChatModel> chatModelProvider, ObjectMapper objectMapper,
                                  AiProperties properties) {
        this.chatModelProvider = chatModelProvider;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.executor = Executors.newFixedThreadPool(WORKER_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "ai-chat-call");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    @CircuitBreaker(name = "aiScoring", fallbackMethod = "fallbackRuleScore")
    @Bulkhead(name = "aiScoring")
    public AiSuggestResult evaluateEmissionReport(Long reportId, String period, BigDecimal totalEnergyKwh,
                                                  String fraudSummaryJson) {
        String answer = callModel(SCORING_SYSTEM_PROMPT, scoringPrompt(period, totalEnergyKwh, fraudSummaryJson));
        return parseSuggestion(answer);
    }

    /** Deterministic 0..100 score from DQ1..DQ8 whenever the LLM fails, times out or is saturated. */
    public AiSuggestResult fallbackRuleScore(Long reportId, String period, BigDecimal totalEnergyKwh,
                                             String fraudSummaryJson, Throwable failure) {
        FraudSummary summary = readSummary(fraudSummaryJson);
        BigDecimal score = summary.weightedScore();
        String notes = "AI không khả dụng (" + failure.getClass().getSimpleName()
                + "). Điểm dự phòng tính từ 8 luật DQ1..DQ8: " + summary.describe();
        log.warn("AI scoring fallback reportId={} score={} cause={}", reportId, score, failure.getMessage());
        return new AiSuggestResult(score, properties.fallbackVersion(), truncate(notes));
    }

    @Override
    @CircuitBreaker(name = "aiChat", fallbackMethod = "fallbackChat")
    @RateLimiter(name = "aiChat")
    public String chatWithAssistant(String roleContext, String question) {
        String normalized = roleContext == null ? "" : roleContext.trim().toUpperCase(java.util.Locale.ROOT);
        String sanitizedRole = sanitizeRoleContext(normalized);
        String userPrompt = "[Vai trò người dùng: " + sanitizedRole + "]\nCâu hỏi: " + question;
        String answer = callModel(CHAT_SYSTEM_PROMPT, userPrompt);
        String trimmed = answer.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalStateException("LLM returned an empty answer");
        }
        return trimmed;
    }

    /** Friendly degradation for the chat widget; it must never raise a 500 to the SPA. */
    public String fallbackChat(String roleContext, String question, Throwable failure) {
        log.warn("AI chat fallback roleContext={} cause={}", roleContext, failure.getMessage());
        return UNAVAILABLE_MESSAGE;
    }

    private String scoringPrompt(String period, BigDecimal totalEnergyKwh, String fraudSummaryJson) {
        return "Kỳ báo cáo: " + period
                + "\nTổng năng lượng (kWh): " + (totalEnergyKwh == null ? "n/a" : totalEnergyKwh.toPlainString())
                + "\nKết quả 8 luật dữ liệu (JSON): " + fraudSummaryJson
                + "\nHãy cho điểm độ tin cậy 0..100 và nhận xét ngắn gọn.";
    }

    private String callModel(String systemPrompt, String userPrompt) {
        ChatClient client = client();
        Future<String> future = executor.submit(
                () -> client.prompt().system(systemPrompt).user(userPrompt).call().content());
        try {
            String content = future.get(properties.timeoutSeconds(), TimeUnit.SECONDS);
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("LLM returned an empty completion");
            }
            return content;
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new IllegalStateException("LLM call timed out after " + properties.timeoutSeconds() + "s", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM call was interrupted", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("LLM call failed", ex.getCause());
        }
    }

    private ChatClient client() {
        ChatClient existing = chatClient;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (chatClient == null) {
                ChatModel model = chatModelProvider.getIfAvailable();
                if (model == null) {
                    throw new IllegalStateException("No Spring AI ChatModel is configured");
                }
                chatClient = ChatClient.create(model);
            }
            return chatClient;
        }
    }

    private AiSuggestResult parseSuggestion(String answer) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(answer));
            JsonNode scoreNode = root.path("score");
            if (!scoreNode.isNumber()) {
                throw new IllegalStateException("AI response has no numeric score");
            }
            BigDecimal score = scoreNode.decimalValue();
            String notes = root.path("notes").isTextual()
                    ? root.path("notes").asText()
                    : root.path("reasoning").asText("");
            return new AiSuggestResult(score, AI_VERSION, truncate(notes));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("AI response is not valid JSON", ex);
        }
    }

    private FraudSummary readSummary(String fraudSummaryJson) {
        if (fraudSummaryJson == null || fraudSummaryJson.isBlank()) {
            return new FraudSummary(List.of());
        }
        try {
            FraudSummary summary = objectMapper.readValue(fraudSummaryJson, FraudSummary.class);
            return summary == null ? new FraudSummary(List.of()) : summary;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Cannot read the fraud summary for the AI fallback: {}", ex.getMessage());
            return new FraudSummary(List.of());
        }
    }

    private static String stripCodeFence(String answer) {
        String trimmed = answer.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineBreak = trimmed.indexOf('\n');
        if (firstLineBreak < 0) {
            return trimmed;
        }
        String body = trimmed.substring(firstLineBreak + 1);
        int closingFence = body.lastIndexOf("```");
        return closingFence < 0 ? body : body.substring(0, closingFence);
    }

    private static String sanitizeRoleContext(String normalized) {
        if (normalized.isEmpty() || normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0) {
            return "AUTHENTICATED_USER";
        }
        String candidate = normalized;
        if (candidate.startsWith("ROLE=")) {
            int sep = candidate.indexOf(';');
            candidate = sep >= 0 ? candidate.substring(5, sep).trim() : candidate.substring(5).trim();
        }
        return switch (candidate) {
            case "ROLE_EV_OWNER", "ROLE_COMPANY", "ROLE_CVA", "ROLE_ADMIN" -> candidate;
            default -> "AUTHENTICATED_USER";
        };
    }

    private static String truncate(String notes) {
        if (notes == null) {
            return null;
        }
        String trimmed = notes.trim();
        return trimmed.length() <= MAX_NOTES_LENGTH ? trimmed : trimmed.substring(0, MAX_NOTES_LENGTH);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
