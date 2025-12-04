package com.agnostik.bot_runner.ws;

import com.agnostik.bot_runner.config.AppProperties;
import com.agnostik.bot_runner.dto.SnapshotDTO;
import com.agnostik.bot_runner.dto.TextUpdateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class StompClientService {

    private final AppProperties props;

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client;
    }

    public StompSession connect(String jwt, Consumer<SnapshotDTO> snapshotHandler) {
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwt);

        WebSocketStompClient client = stompClient();
        return client.connectAsync(
                props.getWsEndpoint(),
                handshake,
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        session.subscribe("/user/queue/snapshot", new StompFrameHandler() {
                            @Override
                            public Type getPayloadType(StompHeaders headers) {
                                return SnapshotDTO.class;
                            }

                            @Override
                            public void handleFrame(StompHeaders headers, Object payload) {
                                snapshotHandler.accept((SnapshotDTO) payload);
                            }
                        });
                    }
                }
        ).join();
    }

    public void sendText(StompSession session, String text) {
        session.send("/app/text", new TextUpdateDTO(text));
    }
}
