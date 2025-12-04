package com.agnostik.bot_runner.bot;

import com.agnostik.bot_runner.config.AppProperties;
import com.agnostik.bot_runner.config.LlmProperties;
import com.agnostik.bot_runner.llm.LlmService;
import com.agnostik.bot_runner.http.HttpClientService;
import com.agnostik.bot_runner.ws.StompClientService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BotManager {

    private final AppProperties props;
    private final HttpClientService http;
    private final StompClientService ws;
    private final LlmService llm;
    private final LlmProperties llmProps;

    private final List<BotSession> sessions = new ArrayList<>();

    @PostConstruct
    public void startAll() {
        if (props.getBots() == null) return;
        for (AppProperties.BotCredential cred : props.getBots()) {
            BotSession session = new BotSession(cred, http, ws, llm, llmProps);
            session.start();
            sessions.add(session);
        }
    }

    @Scheduled(fixedDelay = 8000)
    public void tick() {
        for (BotSession bot : sessions) {
            try {
                bot.decideAndAct();
            } catch (Exception ignored) {
            }
        }
    }
}
