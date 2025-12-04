package com.agnostik.bot_runner.llm;

import com.agnostik.bot_runner.config.LlmProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmService {

    private final WebClient.Builder webClientBuilder;
    private final LlmProperties llmProps;

    private WebClient client() {
        return webClientBuilder
                .baseUrl(llmProps.getUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + resolveApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private String resolveApiKey() {
        if (llmProps.getApiKey() != null && !llmProps.getApiKey().isBlank()) {
            return llmProps.getApiKey();
        }
        if (llmProps.getApiKeyEnv() != null && !llmProps.getApiKeyEnv().isBlank()) {
            return Optional.ofNullable(System.getenv(llmProps.getApiKeyEnv())).orElse("");
        }
        return "";
    }

    public String generate(String prompt) {
        var body = Map.of(
                "model", llmProps.getModel(),
                "messages", new Object[]{
                        Map.of("role", "user", "content", prompt)
                },
                "max_tokens", llmProps.getMaxTokens(),
                "temperature", llmProps.getTemperature()
        );

        Mono<Map<String, Object>> mono = client()
                .post()
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofMillis(llmProps.getTimeoutMs()));

        Map<String, Object> response = mono.block();
        if (response == null) return null;

        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof Iterable<?> choices)) return null;
        for (Object choiceObj : choices) {
            if (choiceObj instanceof Map<?, ?> choice) {
                Object message = choice.get("message");
                if (message instanceof Map<?, ?> msg) {
                    Object content = msg.get("content");
                    if (content != null) return content.toString();
                }
            }
        }
        return null;
    }
}
