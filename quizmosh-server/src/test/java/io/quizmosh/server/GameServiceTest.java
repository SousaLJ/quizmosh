package io.quizmosh.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizmosh.domain.common.DomainException;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameServiceTest {
    static class MutableClock extends Clock {
        Instant instant=Instant.parse("2026-01-01T12:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return instant;}
        void advance(long seconds){instant=instant.plusSeconds(seconds);}
    }
    MutableClock clock;
    GameService game;
    Catalog catalog;
    ResultArchive archive;
    GameService.Identity host,guest;
    String code;
    @BeforeEach void setup() throws Exception {
        clock=new MutableClock();catalog=new Catalog(new ObjectMapper());archive=mock(ResultArchive.class);
        game=new GameService(catalog,archive,20,clock);
    }
    GameService.Config config(String mode){return new GameService.Config(4,15,"all",List.of(mode));}
    void room(String mode) {
        var h=game.create(new GameService.CreateRequest("Leandro",false,config(mode)));code=(String)h.get("code");
        host=game.authenticate(code,(String)h.get("token"));
        var g=game.join(code,new GameService.JoinRequest("Amigo","PLAYER"));guest=game.authenticate(code,(String)g.get("token"));
        game.start(host,null);clock.advance(3);game.tick();
    }
    @SuppressWarnings("unchecked") Map<String,Object> map(Object object){return (Map<String,Object>)object;}
    @Test void allModesFinishKeepFinalRankingAndAllowRematch() throws Exception {
        for(String mode:List.of("classic-trivia","quick-fire","guess-it","closest-wins")) {
            room(mode);
            for(int i=0;i<4;i++) {
                var s=game.state(host);assertEquals("ROUND",s.get("phase"));
                String rid=(String)map(s.get("round")).get("id");
                assertEquals(mode,map(s.get("round")).get("mode"));
                // Public projections include neither solutions nor other players' submissions.
                String json=new ObjectMapper().findAndRegisterModules().writeValueAsString(map(s.get("round")));
                assertFalse(json.contains("correctOption"));assertFalse(json.contains("acceptedAnswers"));assertFalse(json.contains("correctValue"));
                String answer=mode.equals("guess-it")?"Um palpite":mode.equals("closest-wins")?"100":"A";
                var result=game.answer(host,new GameService.AnswerRequest(rid,answer));assertTrue(result.accepted());
                if(!mode.equals("guess-it")){assertNull(result.correct());assertNull(result.scoreDelta());}
                assertNull(map(game.state(guest).get("you")).get("answer"));
                clock.advance(16);game.tick();
                assertNotNull(game.state(host).get("reveal"));
                if(i<3)game.next(host);
            }
            var finalState=game.state(host);assertEquals("FINISHED",finalState.get("phase"));
            assertEquals(2,((List<?>)finalState.get("ranking")).size());assertNotNull(finalState.get("matchId"));
            game.tick();assertEquals(true,game.state(host).get("archived"));
            game.start(host,null);assertEquals("COUNTDOWN",game.state(host).get("phase"));
            assertNull(game.state(host).get("reveal"));
        }
        verify(archive,atLeast(4)).save(anyString(),anyString(),any());
    }
    @Test void duplicateConcurrentAnswersAreAppliedOnlyOnce() throws Exception {
        room("classic-trivia");String rid=(String)map(game.state(host).get("round")).get("id");
        try(var executor=Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> work=new ArrayList<>();
            for(int i=0;i<20;i++)work.add(()->game.answer(host,new GameService.AnswerRequest(rid,"A")).accepted());
            int accepted=0;for(var f:executor.invokeAll(work))if(f.get())accepted++;
            assertEquals(1,accepted);
        }
    }
    @Test void authorizationAndStaleRoundCannotChangeGame() {
        room("classic-trivia");
        assertThrows(DomainException.class,()->game.start(guest,null));
        assertThrows(DomainException.class,()->game.next(guest));
        assertThrows(ApiException.class,()->game.authenticate(code,"invalid"));
        assertThrows(ApiException.class,()->game.answer(host,new GameService.AnswerRequest("stale-round","A")));
        assertEquals(false,map(game.state(host).get("you")).get("submitted"));
    }
    @Test void spectatorsCannotAnswerAndJoinIsBlockedMidGameForPlayers() {
        room("classic-trivia");
        assertThrows(ApiException.class,()->game.join(code,new GameService.JoinRequest("Atrasado","PLAYER")));
        var watcher=game.join(code,new GameService.JoinRequest("TV","DISPLAY"));var id=game.authenticate(code,(String)watcher.get("token"));
        assertEquals(false,map(game.state(id).get("you")).get("canAnswer"));
        String rid=(String)map(game.state(id).get("round")).get("id");
        assertThrows(DomainException.class,()->game.answer(id,new GameService.AnswerRequest(rid,"A")));
    }
    @Test void hostLeavesOwnershipTransfersAndTokenIsRevoked() {
        room("classic-trivia");game.leave(host);
        assertEquals(true,map(game.state(guest).get("you")).get("owner"));
        assertThrows(ApiException.class,()->game.authenticate(code,host.token()));
        clock.advance(16);game.tick();assertEquals("REVEAL",game.state(guest).get("phase"));
    }
    @Test void hostDisconnectTransfersAfterGracePeriod() {
        room("classic-trivia");clock.advance(46);game.authenticate(code,guest.token());game.tick();
        assertEquals(true,map(game.state(guest).get("you")).get("owner"));
    }
    @Test void cluesUnlockAnotherGuessAndDoNotLeakFutureClues() {
        room("guess-it");var s=game.state(host);String rid=(String)map(s.get("round")).get("id");
        assertEquals(1,((List<?>)map(s.get("round")).get("clues")).size());
        assertTrue(game.answer(host,new GameService.AnswerRequest(rid,"incorreto")).accepted());
        assertFalse(game.answer(host,new GameService.AnswerRequest(rid,"incorreto")).accepted());
        clock.advance(4);game.tick();
        assertEquals(2,((List<?>)map(game.state(host).get("round")).get("clues")).size());
        assertTrue(game.answer(host,new GameService.AnswerRequest(rid,"incorreto")).accepted());
    }
    @Test void allSupportedContentConfigurationsHaveCapacity() {
        assertEquals(92,catalog.size());
        for(String category:List.of("all","cinema","geral"))for(String mode:List.of("classic-trivia","quick-fire","guess-it","closest-wins")) {
            var h=game.create(new GameService.CreateRequest("Player",true,new GameService.Config(12,25,category,List.of(mode))));
            var id=game.authenticate((String)h.get("code"),(String)h.get("token"));assertDoesNotThrow(()->game.start(id,null));
        }
    }
    @Test void expiredRoomsAreRemovedAndArchiveFailuresDoNotStopPlay() throws Exception {
        room("classic-trivia");clock.advance(1801);game.tick();
        assertThrows(ApiException.class,()->game.authenticate(code,host.token()));
        doThrow(new IllegalStateException("database unavailable")).when(archive).save(anyString(),anyString(),any());
        room("closest-wins");for(int i=0;i<4;i++){clock.advance(16);game.tick();if(i<3)game.next(host);}
        assertEquals("FINISHED",game.state(host).get("phase"));assertEquals(false,game.state(host).get("archived"));
        String oldMatch=(String)game.state(host).get("matchId");
        assertDoesNotThrow(()->game.start(host,null));
        doNothing().when(archive).save(anyString(),anyString(),any());
        clock.advance(31);game.tick();
        verify(archive,atLeast(2)).save(eq(oldMatch),eq(code),any());
    }
    @Test void englishRegionalRoomKeepsTheSameContentForPlayersAndRematches() {
        var cfg=new GameService.Config(4,15,"all",List.of("classic-trivia","quick-fire","guess-it","closest-wins"),false,"en","REGIONAL","BR");
        var created=game.create(new GameService.CreateRequest("Host",false,cfg));code=(String)created.get("code");
        host=game.authenticate(code,(String)created.get("token"));
        var joined=game.join(code,new GameService.JoinRequest("Friend","PLAYER"));guest=game.authenticate(code,(String)joined.get("token"));
        game.start(host,null);clock.advance(3);game.tick();
        for(int round=0;round<4;round++) {
            var shared=map(game.state(host).get("round"));
            assertEquals(shared,map(game.state(guest).get("round")));
            assertEquals("en",shared.get("language"));assertEquals(List.of("BR"),shared.get("regions"));
            clock.advance(16);game.tick();
            assertFalse(((String)map(game.state(host).get("reveal")).get("explanation")).isBlank());
            if(round<3) game.next(host);
        }
        assertEquals("FINISHED",game.state(host).get("phase"));
        game.start(host,null);assertEquals(cfg,game.state(host).get("config"));
        var insufficient=new GameService.Config(12,15,"cinema",List.of("guess-it"),false,"en","REGIONAL","BR");
        assertEquals("error.catalogCapacity",assertThrows(ApiException.class,()->game.create(new GameService.CreateRequest("Host",false,insufficient))).getMessage());
    }
}
