package io.quizmosh.server;

import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MoshGameServiceTest {
    GameServiceTest.MutableClock clock;
    GameService game;
    GameService.Identity host,guest,screen;
    String code;
    @BeforeEach void setup() throws Exception {
        clock=new GameServiceTest.MutableClock();game=new GameService(CatalogTestSupport.catalog(),mock(ResultArchive.class),20,clock);
        var h=game.create(new GameService.CreateRequest("Host",false,new GameService.Config(4,15,"all",List.of("classic-trivia"),true)));
        code=(String)h.get("code");host=game.authenticate(code,(String)h.get("token"));
        guest=join("Guest","PLAYER");screen=join("TV","DISPLAY");
        game.start(host,null);clock.advance(3);game.tick();
    }
    GameService.Identity join(String name,String role) {var joined=game.join(code,new GameService.JoinRequest(name,role));return game.authenticate(code,(String)joined.get("token"));}
    @SuppressWarnings("unchecked") Map<String,Object> map(Object value) {return (Map<String,Object>)value;}
    Map<String,Object> mosh(GameService.Identity id) {return map(game.state(id).get("mosh"));}
    GameService.PlanRequest plan(String card,String target) {return new GameService.PlanRequest((String)mosh(host).get("stageId"),card,target);}
    @Test void planningHidesCardsCostsAndQuestionUntilEveryoneCommits() {
        assertEquals("BACKSTAGE",game.state(host).get("phase"));assertNull(game.state(host).get("round"));
        game.plan(host,plan("ALL_IN",null));game.plan(host,plan("ALL_IN",null));
        assertEquals(1,map(mosh(host).get("energy")).get(host.player().value()));
        assertEquals(3,map(mosh(guest).get("energy")).get(host.player().value()));
        assertEquals(3,map(mosh(screen).get("energy")).get(host.player().value()));
        assertEquals(1,map(mosh(host).get("plans")).size());assertTrue(map(mosh(guest).get("plans")).isEmpty());
        assertThrows(ApiException.class,()->game.plan(screen,plan("STEADY",null)));
        assertThrows(ApiException.class,()->game.plan(guest,new GameService.PlanRequest("stale","STEADY",null)));
        assertThrows(ApiException.class,()->game.plan(host,plan("STEADY",null)));
        assertThrows(ApiException.class,()->game.answer(host,new GameService.AnswerRequest("unknown","A")));
        game.plan(guest,plan("DUET",host.player().value()));clock.advance(3);game.tick();
        assertEquals("ROUND",game.state(host).get("phase"));assertEquals(2,map(mosh(screen).get("plans")).size());
        assertEquals(1,map(mosh(guest).get("energy")).get(host.player().value()));
    }
    @Test void timeoutDefaultsCardsAppliesPenaltyArchivesAndRematchResets() {
        game.plan(host,plan("ALL_IN",null));clock.advance(26);game.tick();
        assertEquals("ROUND",game.state(host).get("phase"));
        clock.advance(16);game.tick();
        assertEquals(-300,map(map(game.state(host).get("reveal")).get("deltas")).get(host.player().value()));
        assertEquals(-300,map(map(mosh(host).get("results")).get(host.player().value())).get("total"));
        for(int n=2;n<=4;n++) {
            game.next(host);assertEquals("BACKSTAGE",game.state(host).get("phase"));
            clock.advance(17);game.tick();clock.advance(16);game.tick();
        }
        assertEquals("FINISHED",game.state(host).get("phase"));assertEquals(true,game.state(host).get("archived"));
        game.start(host,null);assertEquals(3,map(mosh(host).get("energy")).get(host.player().value()));
        assertEquals(0,mosh(host).get("heat"));assertTrue(map(mosh(host).get("plans")).isEmpty());
    }
    @Test void automatedPracticeCompletesWithTactics() {
        var h=game.create(new GameService.CreateRequest("Solo",true,new GameService.Config(4,15,"all",List.of("classic-trivia","quick-fire","guess-it","closest-wins"),true)));
        var id=game.authenticate((String)h.get("code"),(String)h.get("token"));game.start(id,null);
        for(int i=0;i<260 && !game.state(id).get("phase").equals("FINISHED");i++) {clock.advance(1);game.tick();}
        assertEquals("FINISHED",game.state(id).get("phase"));
        assertEquals(4,map(mosh(id).get("results")).size());
        assertEquals(4,((List<?>)game.state(id).get("ranking")).size());
    }
    @Test void offlineParticipantDoesNotBlockBackstageAndLateSpectatorCannotPlan() {
        game.plan(host,plan("DUET",guest.player().value()));game.leave(guest);
        var late=join("Watcher","SPECTATOR");assertThrows(ApiException.class,()->game.plan(late,plan("STEADY",null)));
        clock.advance(26);game.tick();assertEquals("ROUND",game.state(host).get("phase"));
        clock.advance(16);game.tick();assertEquals("REVEAL",game.state(host).get("phase"));
    }
}
