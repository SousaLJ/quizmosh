package io.quizmosh.domain.game;

import io.quizmosh.domain.common.*;
import io.quizmosh.domain.quiz.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.quizmosh.domain.game.MoshSession.Card.*;

class MoshSessionTest {
    final ParticipantId a=ParticipantId.of("a"),b=ParticipantId.of("b"),c=ParticipantId.of("c");
    final Instant now=Instant.parse("2026-09-16T12:00:00Z");
    final QuestionDefinition question=new QuestionDefinition(QuestionId.of("q"),PackId.of("p"),CategoryId.of("c"),
            Set.of(CoreGameModes.CLASSIC_TRIVIA),Difficulty.EASY,"Q?",
            new ChoiceContent(List.of(new ChoiceOption("A","Yes"),new ChoiceOption("B","No")),"A"),Set.of());
    GameRound round(int number,Map<ParticipantId,Integer> points,GameModeId mode) {
        GameRound r=new GameRound(RoundId.of("r"+number),number,mode,question,points.keySet(),now,Duration.ofSeconds(15));
        points.forEach((id,p)-> {if(p!=0)r.recordSubmission(new SubmittedAnswer(id,new ChoiceAnswer(p>0?"A":"B"),now,0,p>0,p),true);});
        r.close();return r;
    }
    RoundOutcome finish(MoshSession m,int number,Map<ParticipantId,Integer> points) {
        m.seal();GameRound r=round(number,points,CoreGameModes.CLASSIC_TRIVIA);
        return m.settle(r,new RoundOutcome(r.id(),points,now));
    }
    @Test void sharedSpotlightAndReciprocalDuetChangeTotals() {
        MoshSession m=new MoshSession(List.of(a,b));m.prepare(1);
        m.commit(a,SPOTLIGHT,null);m.commit(b,SPOTLIGHT,null);
        assertEquals(Map.of(a,1300,b,1300),finish(m,1,Map.of(a,1000,b,1000)).scoreDeltas());
        m.prepare(2);m.commit(a,DUET,b);m.commit(b,DUET,a);
        assertEquals(Map.of(a,1600,b,1600),finish(m,2,Map.of(a,1000,b,1000)).scoreDeltas());
        assertEquals(600,m.results().get(a).bonus());
    }
    @Test void duetPaysForReadingPartnerEvenWhenOwnAnswerFails() {
        MoshSession m=new MoshSession(List.of(a,b));m.prepare(1);m.commit(a,DUET,b);
        assertEquals(250,finish(m,1,Map.of(a,0,b,1000)).scoreDeltas().get(a));
        assertEquals(4,m.energy().get(a));assertEquals(4,m.energy().get(b));
    }
    @Test void choicesAreLockedAndRetriesDoNotSpendAgain() {
        MoshSession m=new MoshSession(List.of(a,b));m.prepare(1);
        assertThrows(DomainException.class,()->m.commit(a,DUET,a));
        assertThrows(DomainException.class,()->m.commit(a,DUET,c));
        assertThrows(DomainException.class,()->m.commit(c,STEADY,null));
        m.commit(a,ALL_IN,null);m.commit(a,ALL_IN,null);assertEquals(1,m.energy().get(a));
        assertThrows(DomainException.class,()->m.commit(a,STEADY,null));
        finish(m,1,Map.of(a,1000,b,0));m.prepare(2);m.commit(a,ALL_IN,null);finish(m,2,Map.of(a,1000,b,0));
        m.prepare(3);assertThrows(DomainException.class,()->m.commit(a,ALL_IN,null));
    }
    @Test void collectiveHeatAnnouncesEncoreBeforeChoicesAndDoublesRiskToo() {
        MoshSession m=new MoshSession(List.of(a,b));
        for(int n=1;n<=3;n++) {m.prepare(n);assertFalse(m.encore());finish(m,n,Map.of(a,1000,b,1000));}
        assertEquals(100,m.heat());assertEquals(5,m.energy().get(a));
        m.prepare(4);assertTrue(m.encore());assertEquals(0,m.heat());
        m.commit(a,ALL_IN,null);m.commit(b,ALL_IN,null);
        assertEquals(Map.of(a,3000,b,-600),finish(m,4,Map.of(a,1000,b,0)).scoreDeltas());
        assertEquals(20,m.heat());
        assertThrows(DomainException.class,()->m.settle(round(4,Map.of(a,1000,b,0),CoreGameModes.CLASSIC_TRIVIA),new RoundOutcome(RoundId.of("r4"),Map.of(a,1000,b,0),now)));
    }
    @Test void proximitySuccessIncludesSecondPlaceButNotThird() {
        MoshSession m=new MoshSession(List.of(a,b,c));m.prepare(1);m.commit(b,ALL_IN,null);m.commit(c,ALL_IN,null);m.seal();
        var points=Map.of(a,1000,b,600,c,300);var r=round(1,points,CoreGameModes.CLOSEST_WINS);
        assertEquals(Map.of(a,1000,b,1200,c,0),m.settle(r,new RoundOutcome(r.id(),points,now)).scoreDeltas());
        assertFalse(m.results().get(c).success());
    }
    @Test void skippedChoiceDefaultsToSteadyAndMissedAllInCostsPoints() {
        MoshSession m=new MoshSession(List.of(a,b));m.prepare(1);m.commit(a,ALL_IN,null);
        assertEquals(Map.of(a,-300,b,0),finish(m,1,Map.of(a,0,b,0)).scoreDeltas());
        assertEquals(STEADY,m.plans().get(b).card());
        assertThrows(DomainException.class,()->m.commit(b,DUET,a));
    }
}
