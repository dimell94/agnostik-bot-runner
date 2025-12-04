package com.agnostik.bot_runner.bot;

import com.agnostik.bot_runner.config.AppProperties;
import com.agnostik.bot_runner.config.LlmProperties;
import com.agnostik.bot_runner.dto.AuthenticationResponseDTO;
import com.agnostik.bot_runner.dto.SnapshotDTO;
import com.agnostik.bot_runner.http.HttpClientService;
import com.agnostik.bot_runner.llm.LlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.stomp.StompSession;
import com.agnostik.bot_runner.ws.StompClientService;


import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RequiredArgsConstructor
public class BotSession {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AppProperties.BotCredential cred;
    private final HttpClientService http;
    private final StompClientService ws;
    private final LlmService llm;
    private final LlmProperties llmProps;

    @Getter
    private final AtomicReference<SnapshotDTO> lastSnapshot = new AtomicReference<>();
    @Getter
    private String jwt;
    @Getter
    private StompSession session;
    private long lastDecisionAt = 0;
    private final AtomicBoolean typing = new AtomicBoolean(false);
    private final ScheduledExecutorService typingExecutor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<String> lastText = new AtomicReference<>("");

    public void start() {
        AuthenticationResponseDTO auth = loginOrRegister();
        this.jwt = auth.getToken();
        this.session = ws.connect(jwt, this::onSnapshot);
    }

    private AuthenticationResponseDTO loginOrRegister() {
        try {
            return http.login(cred.getUsername(), cred.getPassword());
        } catch (Exception e) {
            AuthenticationResponseDTO reg = http.register(cred.getUsername(), cred.getPassword());
            if (reg == null) {
                throw new IllegalStateException("Register failed for " + cred.getUsername());
            }
            return http.login(cred.getUsername(), cred.getPassword());
        }
    }

    private void onSnapshot(SnapshotDTO snapshot) {
        lastSnapshot.set(snapshot);
        

        handleFriendRequests(snapshot);
    }


    public void moveLeft() { http.moveLeft(jwt); }
    public void moveRight() { http.moveRight(jwt); }
    public void lock() { http.lock(jwt); }
    public void unlock() { http.unlock(jwt); }
    public void leave() { http.leave(jwt); }
    public void sendText(String text) { ws.sendText(session, text); }
    public void sendRequest(String direction) { http.sendRequest(direction, jwt); }
    public void accept(String direction) { http.accept(direction, jwt); }
    public void reject(String direction) { http.reject(direction, jwt); }


    public void decideAndAct() {
        if (typing.get()) return;

        if (cred.getUseLlm() == null || !cred.getUseLlm()) {
            runFixedBehavior();
            return;
        }

        if (llmProps.getEnabled() == null || !llmProps.getEnabled()) return;

        long now = System.currentTimeMillis();
        if (now - lastDecisionAt < llmProps.getMinIntervalMs()) return;

        SnapshotDTO snap = lastSnapshot.get();
        if (snap == null) return;

        String prompt = buildPrompt(snap);
        String response;
        System.out.println("decide for " + cred.getUsername() + " at " + System.currentTimeMillis());

        try {
            response = llm.generate(prompt);
            System.out.println("llm response for " + cred.getUsername() + ": " + response);
        } catch (Exception e) {
             System.out.println("llm error for " + cred.getUsername() + ": " + e.getMessage());
            return;
        }
        if (response == null || response.isBlank()) return;

        Action action = parseAction(response);
        System.out.println("parsed action for " + cred.getUsername() + ": " + action);
        if (action == null) return;

        applyAction(snap, action);
        System.out.println("applied action for " + cred.getUsername());
        lastDecisionAt = now;
    }

   private String buildPrompt(SnapshotDTO snap) {
    StringBuilder sb = new StringBuilder();
    sb.append("You are a bot in a corridor chat. You may have a left/right neighbor.\n");
    sb.append("Behaviors:\n");
    sb.append("- Be very chatty by default: send long messages trying to start conversations. Always read any real user text and reply to it.\n");
    sb.append("- A REAL USER does NOT have username like: bot1, bot2, bot3, bot4.\n");
    sb.append("- If a real user has non-empty text, immediately send a short reply to them (never stay silent).\n");
    sb.append("- Stay mostly unlocked; lock occasionally when chatting with a real user, unlock soon after.\n");
    sb.append("- You can move left/right (if neighbor exists), send/accept/reject friend requests to immediate neighbors, and send text.\n");
    sb.append("Message cadence and variation:\n");
    sb.append("- Never repeat the exact same text twice in a row. Vary wording/length.\n");
    sb.append("- If you have no active real user, still send something short/quirky (no empty text).\n");
    sb.append("- Prefer a short message over silence; keep responses coming frequently.\n");
    sb.append("Priorities each step:\n");
    sb.append("1) If neighbor has text (real user active), clear your spam and send a brief reply to them; you may lock while conversing, then unlock soon.\n");
    sb.append("2) If no active real user, send a short playful line; keep unlocked.\n");
    sb.append("3) Handle friend requests: if you have incoming requests, decide accept/reject; you may also send a request to a neighbor.\n");
    sb.append("4) Move left/right if you want to explore or sit by someone.\n");
    sb.append("Always respond with ONLY JSON (no prose) using this schema: ");
    sb.append("{\"move\":\"left|right|none\",\"lock\":\"lock|unlock|none\",\"text\":\"string or empty\",\"request\":\"left|right|accept|reject|none\"}.\n");
    sb.append("If you have no valid action, use none/empty.\n");
    sb.append("State:\n");
    sb.append("me: id=").append(snap.getMe() != null ? snap.getMe().getId() : null)
            .append(", locked=").append(snap.getMe() != null && snap.getMe().isLocked())
            .append(", index=").append(snap.getMe() != null ? snap.getMe().getMyIndex() : null)
            .append("\n");
    sb.append("corridor size: ").append(snap.getCorridor() != null ? snap.getCorridor().getSize() : null).append("\n");
    sb.append("left neighbor: ").append(describeNeighbor(snap.getLeft())).append("\n");
    sb.append("right neighbor: ").append(describeNeighbor(snap.getRight())).append("\n");
    sb.append("Make one concise decision. If no good action, use none/empty.\n");
    return sb.toString();
}


    private String describeNeighbor(SnapshotDTO.NeighborView n) {
        if (n == null) return "none";
        return "id=" + n.getId() +
                ", locked=" + n.isLocked() +
                ", friend=" + n.isFriend() +
                ", reqToMe=" + n.isRequestToMe() +
                ", reqFromMe=" + n.isRequestFromMe() +
                ", text=" + n.getText();
    }

    private Action parseAction(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            String move = textOrEmpty(root, "move");
            String lock = textOrEmpty(root, "lock");
            String text = textOrEmpty(root, "text");
            String request = textOrEmpty(root, "request");

            if (!isOneOf(move, "left", "right", "none")) move = "none";
            if (!isOneOf(lock, "lock", "unlock", "none")) lock = "none";
            if (!isOneOf(request, "left", "right", "accept", "reject", "none")) request = "none";

            return new Action(move, lock, text, request);
        } catch (Exception e) {
            return null;
        }
    }

    private void applyAction(SnapshotDTO snap, Action a) {

        if ("left".equals(a.move) && snap.getLeft() != null) moveLeft();
        else if ("right".equals(a.move) && snap.getRight() != null) moveRight();


        if ("lock".equals(a.lock)) lock();
        else if ("unlock".equals(a.lock)) unlock();

        
        if ("left".equals(a.request) && snap.getLeft() != null) sendRequest("left");
        else if ("right".equals(a.request) && snap.getRight() != null) sendRequest("right");
        else if ("accept".equals(a.request)) {
            if (snap.getLeft() != null && snap.getLeft().isRequestToMe()) accept("left");
            if (snap.getRight() != null && snap.getRight().isRequestToMe()) accept("right");
        } else if ("reject".equals(a.request)) {
            if (snap.getLeft() != null && snap.getLeft().isRequestToMe()) reject("left");
            if (snap.getRight() != null && snap.getRight().isRequestToMe()) reject("right");
        }


        if (a.text != null && !a.text.isBlank()) {
            String trimmed = a.text;
            if (trimmed.length() > 1000) trimmed = trimmed.substring(0, 1000);
            typeText(trimmed);
        }
    }

    private void runFixedBehavior() {
        SnapshotDTO snap = lastSnapshot.get();
        if (snap == null) return;
        var rnd = ThreadLocalRandom.current();

        
        if (rnd.nextBoolean()) {
            if (snap.getLeft() != null && snap.getRight() != null) {
                if (rnd.nextBoolean()) moveLeft(); else moveRight();
            } else if (snap.getLeft() != null) {
                moveLeft();
            } else if (snap.getRight() != null) {
                moveRight();
            }
        }

        
        if (snap.getMe() != null) {
            if (snap.getMe().isLocked()) {
                if (rnd.nextDouble() < 0.8) unlock();
            } else {
                if (rnd.nextDouble() < 0.2) {
                    lock();
                    typingExecutor.schedule(this::unlock, 1200, TimeUnit.MILLISECONDS);
                }
            }
        }

        String fixed = cred.getFixedText();
        if (fixed == null || fixed.isBlank()) return;
        
        typeText(fixed);
}


    private void typeText(String fullText) {
        if (fullText == null || fullText.isBlank()) return;
        if (!typing.compareAndSet(false, true)) return;

        final int chunkSize = 4;
        long delayMs = 0;

        
        String previous = lastText.get();
        for (int len = previous.length() - 1; len >= 0; len--) {
            String partial = previous.substring(0, len);
            long scheduledDelay = delayMs;
            typingExecutor.schedule(() -> sendText(partial), scheduledDelay, TimeUnit.MILLISECONDS);
            delayMs += 25L;
        }

        for (int end = chunkSize; end <= fullText.length(); end += chunkSize) {
            int actualEnd = Math.min(end, fullText.length());
            String prefix = fullText.substring(0, actualEnd);
            long scheduledDelay = delayMs;
            typingExecutor.schedule(() -> sendText(prefix), scheduledDelay, TimeUnit.MILLISECONDS);
            delayMs += 100L;
        }

        typingExecutor.schedule(() -> {
            sendText(fullText);
            lastText.set(fullText);
            typing.set(false);
        }, delayMs + 50L, TimeUnit.MILLISECONDS);
    }

    private String textOrEmpty(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return n != null && !n.isNull() ? n.asText("") : "";
    }

    private boolean isOneOf(String v, String... opts) {
        for (String o : opts) if (o.equalsIgnoreCase(v)) return true;
        return false;
    }

    private void handleFriendRequests(SnapshotDTO snap) {
        var rnd = ThreadLocalRandom.current();
        if (snap.getLeft() != null && snap.getLeft().isRequestToMe()) {
            if (rnd.nextBoolean()) accept("left"); else reject("left");
        }
        if (snap.getRight() != null && snap.getRight().isRequestToMe()) {
            if (rnd.nextBoolean()) accept("right"); else reject("right");
        }
    }

    private record Action(String move, String lock, String text, String request) {}

    @PreDestroy
    private void shutdownTyping() {
        typingExecutor.shutdownNow();
    }
}
