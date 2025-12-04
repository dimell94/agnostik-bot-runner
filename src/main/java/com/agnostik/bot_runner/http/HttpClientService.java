package com.agnostik.bot_runner.http;

import com.agnostik.bot_runner.config.AppProperties;
import com.agnostik.bot_runner.dto.AuthenticationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class HttpClientService {

    private final WebClient.Builder webClientBuilder;
    private final AppProperties props;

    private WebClient client() {
        return webClientBuilder.baseUrl(props.getBaseUrl()).build();
    }

    private Consumer<HttpHeaders> auth(String jwt) {
        return h -> h.setBearerAuth(jwt);
    }

    public AuthenticationResponseDTO login(String username, String password) {
        return client().post()
                .uri("/api/auth/login")
                .bodyValue(Map.of("username", username, "password", password))
                .retrieve()
                .bodyToMono(AuthenticationResponseDTO.class)
                .block();
    }

    public AuthenticationResponseDTO register(String username, String password) {
        return client().post()
                .uri("/api/auth/register")
                .bodyValue(Map.of("username", username, "password", password))
                .retrieve()
                .bodyToMono(AuthenticationResponseDTO.class)
                .block();
    }

    public void moveLeft(String jwt) { post("/api/presence/moveLeft", jwt); }
    public void moveRight(String jwt) { post("/api/presence/moveRight", jwt); }
    public void lock(String jwt) { post("/api/presence/lock", jwt); }
    public void unlock(String jwt) { post("/api/presence/unlock", jwt); }
    public void leave(String jwt) { post("/api/presence/leave", jwt); }

    public void sendRequest(String direction, String jwt) { post("/api/requests/send/" + direction, jwt); }
    public void accept(String direction, String jwt) { post("/api/requests/accept/" + direction, jwt); }
    public void reject(String direction, String jwt) { post("/api/requests/reject/" + direction, jwt); }

    private void post(String path, String jwt) {
        client().post()
                .uri(path)
                .headers(auth(jwt))
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}

