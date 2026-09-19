package io.quizmosh.server;

import io.quizmosh.application.QuizMoshApplicationService;
import io.quizmosh.application.command.*;
import io.quizmosh.application.view.AnswerReceipt;
import io.quizmosh.domain.common.*;
import io.quizmosh.domain.game.*;
import io.quizmosh.domain.quiz.*;
import io.quizmosh.domain.room.*;
import io.quizmosh.inmemory.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One lock per room protects the complete read/modify/project cycle of the mutable core. */
@Service
public final class GameService {
    private static final Logger LOG=LoggerFactory.getLogger(GameService.class);
    private static final List<String> MODES=List.of("classic-trivia","quick-fire","guess-it","closest-wins");
    private final ConcurrentMap<String, LiveRoom> rooms=new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PendingResult> pendingResults=new ConcurrentHashMap<>();
    private final SecureRandom random=new SecureRandom();
    private final Clock clock;
    private final Catalog catalog;
    private final ResultArchive archive;
    private final int maxRooms;
    private volatile Consumer<String> broadcaster=ignored->{};
    private io.quizmosh.application.account.AnalyticsPort analytics=(s,e,r,m,f)->{};
    @org.springframework.beans.factory.annotation.Autowired
    public void setAnalytics(io.quizmosh.application.account.AnalyticsPort analytics) {this.analytics=analytics;}
    public boolean roomExists(String code) {return rooms.containsKey(code);}
    public void analyticsParticipant(String code,String player,String subject) {
        LiveRoom r=require(code);synchronized(r) {r.analyticsSubjects.put(player,subject);}
    }
    private void track(LiveRoom r,String event) {
        new HashSet<>(r.analyticsSubjects.values()).forEach(subject->analytics.record(subject,event,r.room.code().value(),r.match.id().value(),null));
    }
    public record Config(int rounds,int seconds,String category,List<String> modes,Boolean mosh,
                         String questionLanguage,String contentScope,String questionRegion) {
        public Config(int rounds,int seconds,String category,List<String> modes) {this(rounds,seconds,category,modes,false);}
        public Config(int rounds,int seconds,String category,List<String> modes,Boolean mosh) {
            this(rounds,seconds,category,modes,mosh,"pt-BR","ALL","BR");
        }
    }
    public record CreateRequest(String nickname,boolean practice,Config config) {}
    public record JoinRequest(String nickname,String role) {}
    public record AnswerRequest(String roundId,String value) {}
    public record PlanRequest(String stageId,String card,String target) {}
    public record Identity(String code,ParticipantId player,String token) {}
    private static final class PendingResult {
        final String id, code;
        final Map<String,Object> payload;
        Instant nextTry=Instant.EPOCH;
        PendingResult(String id,String code,Map<String,Object> payload) {this.id=id;this.code=code;this.payload=payload;}
    }
    private static final class Guest {
        final ParticipantId id;
        final Instant expires;
        Instant seen;
        Guest(ParticipantId id, Instant now) {this.id=id;this.seen=now;this.expires=now.plus(Duration.ofHours(12));}
    }
    private static final class LiveRoom {
        final InMemoryRoomStore roomStore=new InMemoryRoomStore();
        final InMemoryMatchStore matchStore=new InMemoryMatchStore();
        final QuizMoshApplicationService app;
        final Map<String,Guest> guests=new HashMap<>();
        final Map<String,String> analyticsSubjects=new HashMap<>();
        final Set<ParticipantId> bots=new HashSet<>();
        final Map<ParticipantId,String> names=new LinkedHashMap<>();
        final Map<ParticipantId,Instant> botDue=new HashMap<>();
        final Instant created;
        Room room;
        Match match;
        Config config;
        MoshSession mosh;
        String stageId;
        Instant planningStarted;
        String phase="LOBBY";
        Instant transitionAt;
        Instant lastBroadcast=Instant.EPOCH;
        Map<String,Object> reveal;
        boolean archived;
        long revision;
        LiveRoom(Catalog catalog, Clock clock) {
            this.created=clock.instant();
            app=new QuizMoshApplicationService(roomStore,matchStore,catalog,e->{},new UuidIdGenerator(),
                    new RandomRoomCodeGenerator(),clock::instant,GameModeRegistry.defaults(),
                    (round,base)->mosh==null?base:mosh.settle(round,base));
        }
    }
    @org.springframework.beans.factory.annotation.Autowired
    public GameService(Catalog catalog,ResultArchive archive,@Value("${quizmosh.max-rooms:250}") int maxRooms) {
        this(catalog,archive,maxRooms,Clock.systemUTC());
    }
    GameService(Catalog catalog,ResultArchive archive,int maxRooms,Clock clock) {
        this.catalog=catalog;this.archive=archive;this.maxRooms=maxRooms;this.clock=clock;
    }
    public void setBroadcaster(Consumer<String> broadcaster) {this.broadcaster=broadcaster;}
    public Map<String,Object> metadata() {return obj("name","QuizMosh","version","0.5.0","questions",catalog.size(),"modes",MODES,
            "questionLanguages",List.of("pt-BR","en"),"questionRegions",List.of("BR"),"contentScopes",List.of("ALL","GLOBAL","REGIONAL"),
            "catalog",catalog.inventory());}

    public synchronized Map<String,Object> create(CreateRequest request) {
        if(rooms.size()>=maxRooms) throw new ApiException(503,"error.roomsBusy");
        Config config=validate(request.config());
        String name=nickname(request.nickname());
        LiveRoom r;
        String code;
        do {
            r=new LiveRoom(catalog,clock);
            var created=r.app.createRoom(new CreateRoomCommand(name,new RoomSettings(12,true,true,true)));
            r.room=r.roomStore.find(created.room().id()).orElseThrow();
            code=r.room.code().value();
        } while(rooms.containsKey(code));
        r.config=config;
        r.names.put(r.room.ownerId(),name);
        if(request.practice()) for(String bot:List.of("Pipoca Bot","Pixel Bot","Disco Bot")) {
            String botName=bot.equalsIgnoreCase(name)?bot+" 2":bot;
            var joined=r.app.joinRoom(new JoinRoomCommand(r.room.code(),botName,ParticipantRole.PLAYER));
            r.bots.add(joined.participantId());r.names.put(joined.participantId(),botName);
        }
        rooms.put(code,r);
        synchronized(r) {return issue(r,r.room.ownerId());}
    }
    public Map<String,Object> join(String code,JoinRequest request) {
        LiveRoom r=require(code);
        synchronized(r) {
            if(r.room.participants().size()>=24) throw new ApiException(409,"error.roomCapacity");
            ParticipantRole role;
            try {role=ParticipantRole.valueOf(request.role()==null?"PLAYER":request.role());}
            catch(IllegalArgumentException e) {throw new ApiException(400,"error.role");}
            // Current match players are fixed by the core. Late arrivals can watch and play the rematch.
            if(!r.phase.equals("LOBBY") && !r.phase.equals("FINISHED") && role==ParticipantRole.PLAYER)
                throw new ApiException(409,"error.lateJoin");
            var joined=r.app.joinRoom(new JoinRoomCommand(r.room.code(),nickname(request.nickname()),role));
            r.names.put(joined.participantId(),nickname(request.nickname()));
            Map<String,Object> result=issue(r,joined.participantId());
            changed(r);return result;
        }
    }
    private Map<String,Object> issue(LiveRoom r,ParticipantId id) {
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        r.guests.put(token,new Guest(id,clock.instant()));
        return obj("token",token,"code",r.room.code().value(),"playerId",id.value(),"state",snapshot(r,id));
    }
    public Identity authenticate(String code,String token) {
        LiveRoom r=require(code);
        synchronized(r) {
            Guest guest=r.guests.get(token);
            if(guest==null || !clock.instant().isBefore(guest.expires) || r.room.participant(guest.id).isEmpty())
                throw new ApiException(401,"error.sessionExpired");
            guest.seen=clock.instant();
            return new Identity(r.room.code().value(),guest.id,token);
        }
    }
    public Map<String,Object> state(Identity identity) {
        LiveRoom r=require(identity.code());
        synchronized(r) {return snapshot(r,identity.player());}
    }
    public void start(Identity identity,Config requested) {
        LiveRoom r=require(identity.code());
        synchronized(r) {
            r.room.requireOwner(identity.player());
            if(!Set.of("LOBBY","FINISHED").contains(r.phase)) throw new ApiException(409,"error.matchStarted");
            Config config=validate(requested==null?r.config:requested);
            MatchSettings settings=settings(config);catalog.validateCapacity(settings);
            if(r.room.players().size()<2) throw new ApiException(409,"error.needPlayers");
            var started=r.app.startMatch(new StartMatchCommand(r.room.id(),identity.player(),settings));
            r.match=r.matchStore.find(started.id()).orElseThrow();
            r.mosh=config.mosh()?new MoshSession(r.match.players()):null;r.stageId=null;
            r.config=config;r.phase="COUNTDOWN";r.transitionAt=clock.instant().plusSeconds(3);
            r.reveal=null;r.archived=false;changed(r);
            LOG.info("Match started: {} ({} players)",r.match.id(),r.match.players().size());
            track(r,"MATCH_STARTED");
        }
    }
    public AnswerReceipt answer(Identity identity,AnswerRequest request) {
        LiveRoom r=require(identity.code());
        synchronized(r) {
            if(!r.phase.equals("ROUND")) throw new ApiException(409,"error.roundClosed");
            GameRound round=r.match.currentRound().orElseThrow();
            if(!round.id().value().equals(request.roundId())) throw new ApiException(409,"error.staleRound");
            String value=request.value();
            if(value==null || value.isBlank() || value.length()>160) throw new ApiException(400,"error.answerLength");
            AnswerValue answer;
            if(round.question().content() instanceof ChoiceContent) answer=new ChoiceAnswer(value);
            else if(round.question().content() instanceof GuessContent) answer=new TextAnswer(value);
            else {
                if(!value.matches("-?\\d{1,9}([.,]\\d{1,4})?")) throw new ApiException(400,"error.numeric");
                answer=new NumericAnswer(new BigDecimal(value.replace(',','.')));
            }
            AnswerReceipt receipt=r.app.submitAnswer(new SubmitAnswerCommand(r.room.id(),identity.player(),answer));
            if(receipt.accepted()) analytics.record(r.analyticsSubjects.get(identity.player().value()),"QUESTION_ANSWERED",identity.code(),r.match.id().value(),null);
            detectClosed(r,round);changed(r);return receipt;
        }
    }
    public void plan(Identity identity,PlanRequest request) {
        LiveRoom r=require(identity.code());
        synchronized(r) {
            if(!r.phase.equals("BACKSTAGE") || !Objects.equals(r.stageId,request.stageId()) || !clock.instant().isBefore(r.transitionAt))
                throw new ApiException(409,"error.staleStage");
            if(!r.match.players().contains(identity.player())) throw new ApiException(403,"error.playersOnly");
            MoshSession.Card card;
            try {card=MoshSession.Card.valueOf(request.card());}
            catch(Exception e) {throw new ApiException(400,"error.card");}
            ParticipantId target=request.target()==null?null:ParticipantId.of(request.target());
            if(target!=null && r.room.participant(target).isEmpty()) throw new ApiException(400,"error.partnerLeft");
            try {r.mosh.commit(identity.player(),card,target);}
            catch(DomainException e) {throw new ApiException(409,e.getMessage());}
            changed(r);
        }
    }
    public void leave(Identity identity) {
        LiveRoom r=require(identity.code());
        synchronized(r) {
            r.app.leaveRoom(new LeaveRoomCommand(r.room.id(),identity.player()));
            r.guests.entrySet().removeIf(e->e.getValue().id.equals(identity.player()));
            if(r.room.players().stream().allMatch(p->r.bots.contains(p.id()))) r.room.close();
            changed(r);
        }
    }
    public void next(Identity identity) {
        LiveRoom r=require(identity.code());
        synchronized(r) {
            r.room.requireOwner(identity.player());
            if(!r.phase.equals("REVEAL")) throw new ApiException(409,"error.nextRound");
            prepareRound(r);changed(r);
        }
    }
    private Config validate(Config config) {
        if(config==null) config=new Config(8,25,"all",MODES,true);
        if(config.rounds()<4 || config.rounds()>12) throw new ApiException(400,"error.rounds");
        if(config.seconds()<15 || config.seconds()>60) throw new ApiException(400,"error.duration");
        if(!Set.of("all","cinema","geral").contains(config.category()==null?"":config.category())) throw new ApiException(400,"error.category");
        if(config.modes()==null || config.modes().isEmpty() || config.modes().size()>4 || !MODES.containsAll(config.modes()) || new HashSet<>(config.modes()).size()!=config.modes().size())
            throw new ApiException(400,"error.modes");
        String language=config.questionLanguage()==null?"pt-BR":config.questionLanguage();
        String scope=config.contentScope()==null?"ALL":config.contentScope();
        String region=config.questionRegion()==null?"BR":config.questionRegion();
        if(!Set.of("pt-BR","en").contains(language)) throw new ApiException(400,"error.questionLanguage");
        if(!Set.of("ALL","GLOBAL","REGIONAL").contains(scope)) throw new ApiException(400,"error.contentScope");
        if(!"BR".equals(region)) throw new ApiException(400,"error.questionRegion");
        Config validated=new Config(config.rounds(),config.seconds(),config.category(),List.copyOf(config.modes()),!Boolean.FALSE.equals(config.mosh()),language,scope,region);
        catalog.validateCapacity(settings(validated));
        return validated;
    }
    private MatchSettings settings(Config c) {
        return new MatchSettings(c.rounds(),c.category().equals("all")?Set.of():Set.of(CategoryId.of(c.category())),
                c.modes().stream().map(GameModeId::of).toList(),Duration.ofSeconds(c.seconds()),c.questionLanguage(),c.contentScope(),c.questionRegion());
    }
    private LiveRoom require(String code) {
        if(code==null || !code.matches("[a-zA-Z0-9]{4,8}")) throw new ApiException(404,"error.roomCode");
        LiveRoom r=rooms.get(code.toUpperCase(Locale.ROOT));
        if(r==null || r.room.status()==RoomStatus.CLOSED) throw new ApiException(404,"error.roomMissing");
        return r;
    }
    private String nickname(String value) {
        if(value==null || value.strip().isEmpty() || value.strip().length()>24 || value.codePoints().anyMatch(Character::isISOControl))
            throw new ApiException(400,"error.nickname");
        return value.strip();
    }
    private void prepareRound(LiveRoom r) {
        if(r.mosh==null) {openRound(r);return;}
        r.mosh.prepare(r.match.completedRounds()+1);
        r.phase="BACKSTAGE";r.reveal=null;r.stageId=UUID.randomUUID().toString();
        r.planningStarted=clock.instant();r.transitionAt=r.planningStarted.plusSeconds(r.mosh.number()==1?25:16);
    }
    private void openRound(LiveRoom r) {
        if(r.mosh!=null) r.mosh.seal();
        r.app.startNextRound(new StartNextRoundCommand(r.room.id(),r.room.ownerId()));
        r.phase="ROUND";r.reveal=null;r.transitionAt=null;r.botDue.clear();
        GameRound round=r.match.currentRound().orElseThrow();
        for(ParticipantId id:r.bots) r.botDue.put(id,round.startedAt().plusMillis(2200+random.nextInt(Math.max(1000,r.config.seconds()*500))));
    }
    private void detectClosed(LiveRoom r,GameRound round) {
        if(r.match.currentRound().isPresent()) return;
        RoundResult outcome=r.match.history().getLast();
        Map<String,Integer> delta=new LinkedHashMap<>();outcome.scoreDeltas().forEach((id,v)->delta.put(id.value(),v));
        r.reveal=obj("roundId",round.id().value(),"number",round.number(),"prompt",round.question().prompt(),
                "answer",catalog.answer(round.question()),"explanation",catalog.entry(round.question().id(),r.config.questionLanguage()).explanation(),
                "deltas",delta,"correctOption",round.question().content() instanceof ChoiceContent c?c.correctOptionId():null);
        r.phase=r.match.status()==MatchStatus.FINISHED?"FINISHED":"REVEAL";
        r.transitionAt=r.phase.equals("REVEAL")?clock.instant().plusSeconds(7):null;
        if(r.phase.equals("FINISHED")) {
            LOG.info("Match finished: {}",r.match.id());
            track(r,"MATCH_COMPLETED");
            pendingResults.put(r.match.id().value(),new PendingResult(r.match.id().value(),r.room.code().value(),
                    obj("config",r.config,"ranking",ranking(r),"rounds",r.match.history(),"mosh",moshSnapshot(r,null))));
        }
    }
    @Scheduled(fixedDelay=250)
    public void tick() {
        Instant now=clock.instant();
        for(var entry:rooms.entrySet()) {
            LiveRoom r=entry.getValue();
            synchronized(r) {
                try {
                    if(r.room.status()==RoomStatus.CLOSED || now.isAfter(r.created.plus(Duration.ofHours(12))) ||
                            r.guests.values().stream().noneMatch(g->now.isBefore(g.seen.plus(Duration.ofMinutes(30))))) {
                        rooms.remove(entry.getKey(),r);broadcaster.accept(entry.getKey());continue;
                    }
                    if(!online(r,r.room.ownerId(),now,45)) {
                        r.room.players().stream().filter(p->!r.bots.contains(p.id()) && online(r,p.id(),now,20))
                                .findFirst().ifPresent(p->r.room.transferOwnership(p.id()));
                    }
                    if((r.phase.equals("COUNTDOWN") || r.phase.equals("REVEAL")) && !now.isBefore(r.transitionAt)) prepareRound(r);
                    if(r.phase.equals("BACKSTAGE")) {
                        if(!now.isBefore(r.planningStarted.plusSeconds(2))) planBots(r);
                        if(!now.isBefore(r.transitionAt) || (r.mosh.allReady() && !now.isBefore(r.planningStarted.plusSeconds(3)))) openRound(r);
                    }
                    if(r.phase.equals("ROUND")) {
                        GameRound round=r.match.currentRound().orElseThrow();
                        if(round.isExpired(now)) {
                            r.app.closeRound(new CloseRoundCommand(r.room.id(),r.room.ownerId()));detectClosed(r,round);
                        } else {
                            if(round.question().content() instanceof GuessContent g) {
                                long interval=Math.max(1000,Duration.between(round.startedAt(),round.endsAt()).toMillis()/g.clues().size());
                                int target=Math.min(g.clues().size()-1,(int)(Duration.between(round.startedAt(),now).toMillis()/interval));
                                while(round.clueIndex()<target) r.app.revealNextClue(new RevealNextClueCommand(r.room.id(),r.room.ownerId()));
                            }
                            playBots(r,round,now);
                        }
                    }
                    if(!now.isBefore(r.lastBroadcast.plusSeconds(1))) changed(r);
                } catch(Exception ex) {LOG.error("Room tick failed for {}",entry.getKey(),ex);}
            }
        }
        // Keep an immutable completed report across rematches and retry without holding a room lock.
        for(PendingResult pending:pendingResults.values()) {
            if(now.isBefore(pending.nextTry)) continue;
            pending.nextTry=now.plusSeconds(30);
            try {
                archive.save(pending.id,pending.code,pending.payload);
                pendingResults.remove(pending.id,pending);
                LiveRoom room=rooms.get(pending.code);
                if(room!=null) synchronized(room) {
                    if(room.match!=null && room.match.id().value().equals(pending.id)) room.archived=true;
                }
            } catch(Exception ex) {LOG.warn("Result archive unavailable; will retry for {}",pending.id);}
        }
    }
    private void playBots(LiveRoom r,GameRound round,Instant now) {
        for(ParticipantId id:r.bots) {
            if(!r.phase.equals("ROUND")) break;
            if(round.isLocked(id) || now.isBefore(r.botDue.getOrDefault(id,Instant.MAX)) || round.hasSubmittedAtCurrentClue(id)) continue;
            boolean correct=random.nextDouble()<0.62;
            AnswerValue value;
            if(round.question().content() instanceof ChoiceContent c) {
                var choices=c.options().stream().filter(o->correct?o.id().equals(c.correctOptionId()):!o.id().equals(c.correctOptionId())).toList();
                value=new ChoiceAnswer(choices.get(random.nextInt(choices.size())).id());
            } else if(round.question().content() instanceof GuessContent g) {
                value=new TextAnswer(correct?g.acceptedAnswers().getFirst():"Não sei ainda");
            } else {
                var n=(NumericContent)round.question().content();
                value=new NumericAnswer(n.correctValue().add(BigDecimal.valueOf(correct?0:random.nextInt(31)-15)));
            }
            r.app.submitAnswer(new SubmitAnswerCommand(r.room.id(),id,value));
            r.botDue.put(id,now.plusSeconds(3));detectClosed(r,round);
        }
    }
    private void planBots(LiveRoom r) {
        // No question has been drawn yet. Bots make decisions with the same information as humans.
        for(ParticipantId id:r.bots) {
            if(r.mosh.plans().containsKey(id) || !r.mosh.energy().containsKey(id)) continue;
            var affordable=Arrays.stream(MoshSession.Card.values()).filter(c->c.cost()<=r.mosh.energy().get(id)).toList();
            var card=affordable.get(random.nextInt(affordable.size()));
            var others=r.match.players().stream().filter(p->!p.equals(id) && r.room.participant(p).isPresent()).toList();
            if(card==MoshSession.Card.DUET && others.isEmpty()) card=MoshSession.Card.STEADY;
            r.mosh.commit(id,card,card==MoshSession.Card.DUET?others.get(random.nextInt(others.size())):null);
        }
    }
    private Map<String,Object> moshSnapshot(LiveRoom r,ParticipantId viewer) {
        if(r.mosh==null) return null;
        Map<String,Object> energy=new LinkedHashMap<>(),plans=new LinkedHashMap<>(),results=new LinkedHashMap<>();
        r.mosh.energy().forEach((id,n)-> {
            var plan=r.mosh.plans().get(id);
            // Hide the amount spent too: otherwise a two-beat spend identifies ALL_IN.
            energy.put(id.value(),n+(r.phase.equals("BACKSTAGE") && !id.equals(viewer) && plan!=null?plan.card().cost():0));
        });
        r.mosh.plans().forEach((id,p)-> {
            if(!r.phase.equals("BACKSTAGE") || id.equals(viewer))
                plans.put(id.value(),obj("card",p.card(),"target",p.target()==null?null:p.target().value()));
        });
        r.mosh.results().forEach((id,b)->results.put(id.value(),obj("base",b.base(),"bonus",b.bonus(),"total",b.total(),
                "card",b.card(),"target",b.target()==null?null:b.target().value(),"success",b.success(),"reasonKey",b.reason(),"reason",Messages.text(b.reason(),Locale.forLanguageTag("pt-BR"),Map.of()))));
        int nextIndex=Math.min(r.match.completedRounds(),r.config.rounds()-1);
        return obj("stageId",r.stageId,"number",r.mosh.number(),"heat",r.mosh.heat(),"encore",r.mosh.encore(),
                "mode",r.match.settings().modeForRound(nextIndex).value(),"energy",energy,"plans",plans,
                "ready",r.mosh.plans().keySet().stream().map(ParticipantId::value).toList(),"results",results);
    }
    private boolean online(LiveRoom r,ParticipantId id,Instant now,int seconds) {
        return r.bots.contains(id) || r.guests.values().stream().anyMatch(g->g.id.equals(id) && now.isBefore(g.seen.plusSeconds(seconds)));
    }
    private void changed(LiveRoom r) {r.revision++;r.lastBroadcast=clock.instant();broadcaster.accept(r.room.code().value());}
    private List<Map<String,Object>> ranking(LiveRoom r) {
        if(r.match==null) return List.of();
        List<Map.Entry<ParticipantId,Integer>> scores=new ArrayList<>(r.match.scores().entrySet());
        scores.sort(Map.Entry.<ParticipantId,Integer>comparingByValue().reversed().thenComparing(e->r.names.getOrDefault(e.getKey(),"")));
        List<Map<String,Object>> result=new ArrayList<>();int position=0;Integer previous=null;
        for(int i=0;i<scores.size();i++) {
            var e=scores.get(i);if(!e.getValue().equals(previous)) position=i+1;previous=e.getValue();
            result.add(obj("id",e.getKey().value(),"nickname",r.names.get(e.getKey()),"score",e.getValue(),"rank",position,"bot",r.bots.contains(e.getKey())));
        }
        return result;
    }
    private Map<String,Object> snapshot(LiveRoom r,ParticipantId viewer) {
        Instant now=clock.instant();
        var participant=r.room.participant(viewer).orElseThrow(()->new ApiException(401,"error.joinAgain"));
        GameRound round=r.match==null?null:r.match.currentRound().orElse(null);
        Map<String,Object> question=null;
        if(round!=null) {
            var q=r.app.match(r.room.id()).currentRound().question();
            question=obj("id",round.id().value(),"number",round.number(),"mode",round.modeId().value(),"prompt",q.prompt(),
                    "type",q.contentType(),"choices",q.choices(),"clues",q.visibleClues(),"unit",q.unit(),
                    "category",round.question().categoryId().value(),"language",r.config.questionLanguage(),
                    "regions",catalog.entry(round.question().id()).regions(),"startedAt",round.startedAt(),"endsAt",round.endsAt(),
                    "clueIndex",round.clueIndex(),"clueCount",round.question().content() instanceof GuessContent g?g.clues().size():0,
                    "answeredCount",round.allSubmissions().size(),"playerCount",round.players().size());
        }
        boolean canPlay=participant.canPlay() && round!=null && round.players().contains(viewer);
        var own=round==null?List.<SubmittedAnswer>of():round.submissionsFor(viewer);
        boolean locked=round!=null && round.isLocked(viewer);
        boolean submitted=round!=null && round.hasSubmittedAtCurrentClue(viewer);
        // Only the owner of a submission gets its payload. Correctness follows the core feedback policy.
        Object ownAnswer=own.isEmpty()?null:displayAnswer(own.getLast().answer());
        Boolean guessCorrect=round!=null && round.modeId().equals(CoreGameModes.GUESS_IT) && !own.isEmpty()?own.getLast().correct():null;
        List<Map<String,Object>> players=r.room.participants().stream().map(p->obj("id",p.id().value(),"nickname",p.nickname(),
                "role",p.role().name(),"owner",r.room.isOwner(p.id()),"bot",r.bots.contains(p.id()),"online",online(r,p.id(),now,20),
                "answered",round!=null && round.isLocked(p.id()))).toList();
        return obj("code",r.room.code().value(),"phase",r.phase,"revision",r.revision,"serverTime",now,"config",r.config,
                "players",players,"you",obj("id",viewer.value(),"owner",r.room.isOwner(viewer),"role",participant.role().name(),
                        "canAnswer",canPlay&&!locked&&!submitted&&!round.isExpired(now),"locked",locked,"submitted",submitted,
                        "answer",ownAnswer,"guessCorrect",guessCorrect),
                "round",question,"reveal",r.reveal,"ranking",ranking(r),"transitionAt",r.transitionAt,
                "matchId",r.match==null?null:r.match.id().value(),"archived",r.archived,"mosh",moshSnapshot(r,viewer));
    }
    private Object displayAnswer(AnswerValue answer) {
        if(answer instanceof ChoiceAnswer c) return c.optionId();
        if(answer instanceof TextAnswer t) return t.text();
        return ((NumericAnswer)answer).value().toPlainString();
    }
    public static Map<String,Object> obj(Object... values) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(int i=0;i<values.length;i+=2) result.put((String)values[i],values[i+1]);
        return result;
    }
}
