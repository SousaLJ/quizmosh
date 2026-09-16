package io.quizmosh.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableWebSocket
public class RoomSocket extends TextWebSocketHandler implements WebSocketConfigurer {
    private record Peer(WebSocketSession socket,Instant connected) {}
    private final Map<String,Peer> peers=new ConcurrentHashMap<>();
    private final Map<String,GameService.Identity> identities=new ConcurrentHashMap<>();
    private final GameService game;
    private final ObjectMapper json;
    public RoomSocket(GameService game,ObjectMapper json) {this.game=game;this.json=json;game.setBroadcaster(this::broadcast);}
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {registry.addHandler(this,"/ws");}
    @Override public void afterConnectionEstablished(WebSocketSession raw) throws Exception {
        if(peers.size()>=512) {raw.close(CloseStatus.SERVICE_OVERLOAD);return;}
        raw.setTextMessageSizeLimit(2048);
        peers.put(raw.getId(),new Peer(new ConcurrentWebSocketSessionDecorator(raw,1000,65536),Instant.now()));
    }
    @Override protected void handleTextMessage(WebSocketSession raw,TextMessage message) throws Exception {
        try {
            var value=json.readTree(message.getPayload());
            var identity=identities.get(raw.getId());
            if(identity==null) {
                identity=game.authenticate(value.path("code").asText(),value.path("token").asText());
                // Each guest may have at most two concurrent sockets (e.g. brief reconnect overlap).
                var same=identities.entrySet().stream().filter(e->e.getValue().token().equals(value.path("token").asText())).toList();
                if(same.size()>=2) close(same.getFirst().getKey());
                identities.put(raw.getId(),identity);
            } else game.authenticate(identity.code(),identity.token());
            send(raw.getId(),identity);
        } catch(Exception e) {raw.close(CloseStatus.POLICY_VIOLATION);}
    }
    private void send(String id,GameService.Identity identity) {
        Peer peer=peers.get(id);if(peer==null) return;
        try {peer.socket().sendMessage(new TextMessage(json.writeValueAsString(game.state(identity))));}
        catch(Exception ex) {close(id);}
    }
    private void broadcast(String code) {identities.forEach((id,identity)->{if(identity.code().equals(code)) send(id,identity);});}
    private void close(String id) {
        Peer peer=peers.remove(id);identities.remove(id);
        if(peer!=null) try {peer.socket().close();} catch(Exception ignored) {}
    }
    @Scheduled(fixedDelay=5000) public void sweep() {
        Instant now=Instant.now();
        peers.forEach((id,peer)->{if(!identities.containsKey(id)&&now.isAfter(peer.connected().plusSeconds(5))) close(id);});
    }
    @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status) {close(session.getId());}
    @Override public void handleTransportError(WebSocketSession session,Throwable ex) {close(session.getId());}
}
