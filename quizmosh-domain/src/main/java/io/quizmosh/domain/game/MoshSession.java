package io.quizmosh.domain.game;

import io.quizmosh.domain.common.*;
import java.util.*;

/** Deterministic party scoring. No question or answer is exposed during planning. */
public final class MoshSession {
    public enum Card {
        STEADY(0), SPOTLIGHT(1), DUET(1), ALL_IN(2);
        private final int cost;
        Card(int cost) { this.cost=cost; }
        public int cost() { return cost; }
    }
    public record Plan(Card card, ParticipantId target) {}
    public record Breakdown(int base, int bonus, int total, Card card, ParticipantId target,
                            boolean success, String reason) {}
    private final Map<ParticipantId,Integer> energy=new LinkedHashMap<>();
    private final Map<ParticipantId,Plan> plans=new LinkedHashMap<>();
    private Map<ParticipantId,Breakdown> results=Map.of();
    private final Set<RoundId> settled=new HashSet<>();
    private int heat, number;
    private boolean encore, planning;

    public MoshSession(Collection<ParticipantId> players) { players.forEach(id->energy.put(id,3)); }
    public void prepare(int roundNumber) {
        if(planning || roundNumber!=number+1) throw new DomainException("Preparação fora de ordem.");
        number=roundNumber;plans.clear();results=Map.of();planning=true;
        encore=heat>=100;
        if(encore) heat=0;
    }
    public void commit(ParticipantId player, Card card, ParticipantId target) {
        if(!planning || !energy.containsKey(player)) throw new DomainException("Você não pode escolher uma jogada agora.");
        Objects.requireNonNull(card);
        if(card==Card.DUET && (target==null || target.equals(player) || !energy.containsKey(target)))
            throw new DomainException("Escolha outro jogador da partida para o dueto.");
        if(card!=Card.DUET && target!=null) throw new DomainException("Esta carta não usa parceiro.");
        Plan requested=new Plan(card,target);
        if(plans.containsKey(player)) {
            if(plans.get(player).equals(requested)) return; // A repeated network request never spends twice.
            throw new DomainException("Sua jogada já está confirmada.");
        }
        if(energy.get(player)<card.cost()) throw new DomainException("Você não tem batidas suficientes para esta carta.");
        energy.compute(player,(id,value)->value-card.cost());plans.put(player,requested);
    }
    public void seal() {
        if(!planning) throw new DomainException("As jogadas já foram reveladas.");
        energy.keySet().forEach(id->plans.putIfAbsent(id,new Plan(Card.STEADY,null)));
        planning=false;
    }
    public RoundOutcome settle(GameRound round, RoundOutcome base) {
        if(planning || round.number()!=number || !round.id().equals(base.roundId()) || settled.contains(round.id()))
            throw new DomainException("Resultado fora de ordem ou já aplicado.");
        Map<ParticipantId,Boolean> success=new HashMap<>();
        for(ParticipantId id:energy.keySet()) {
            boolean hit=round.modeId().equals(CoreGameModes.CLOSEST_WINS)
                    ? base.scoreDeltas().getOrDefault(id,0)>=600
                    : round.submissionsFor(id).stream().anyMatch(SubmittedAnswer::correct);
            success.put(id,hit);
        }
        long spotlights=plans.entrySet().stream().filter(e->e.getValue().card()==Card.SPOTLIGHT && success.get(e.getKey())).count();
        Map<ParticipantId,Integer> totals=new LinkedHashMap<>();
        Map<ParticipantId,Breakdown> details=new LinkedHashMap<>();
        for(ParticipantId id:energy.keySet()) {
            Plan plan=plans.getOrDefault(id,new Plan(Card.STEADY,null));
            int points=base.scoreDeltas().getOrDefault(id,0),bonus=0;
            boolean hit=success.get(id);
            String reason="Batidas guardadas";
            switch(plan.card()) {
                case STEADY -> { }
                case SPOTLIGHT -> {
                    bonus=hit ? 600/(int)spotlights : 0;
                    reason=hit ? (spotlights==1 ? "O holofote é todo seu!" : "Holofote dividido entre "+spotlights+" acertos") : "O holofote escapou";
                }
                case DUET -> {
                    boolean partnerHit=success.getOrDefault(plan.target(),false);
                    Plan partner=plans.get(plan.target());
                    boolean mutual=partner!=null && partner.card()==Card.DUET && id.equals(partner.target());
                    bonus=partnerHit ? 250+(hit?250:0)+(hit&&mutual?100:0) : 0;
                    reason=partnerHit ? (hit ? (mutual ? "Dueto recíproco afinadíssimo!" : "Vocês dois acertaram!") : "Seu parceiro salvou o dueto!") : "O dueto não encaixou";
                }
                case ALL_IN -> { bonus=hit ? Math.max(0,points) : -300;reason=hit ? "A aposta deu show!" : "O risco cobrou seu preço"; }
            }
            bonus*=encore?2:1;
            totals.put(id,points+bonus);
            details.put(id,new Breakdown(points,bonus,points+bonus,plan.card(),plan.target(),hit,reason));
            energy.compute(id,(key,value)->Math.min(5,value+(hit?1:2)));
        }
        heat=Math.min(100,heat+(int)Math.round(40.0*success.values().stream().filter(Boolean::booleanValue).count()/energy.size()));
        results=Collections.unmodifiableMap(details);settled.add(round.id());
        return new RoundOutcome(base.roundId(),totals,base.endedAt());
    }
    public Map<ParticipantId,Integer> energy() { return Collections.unmodifiableMap(energy); }
    public Map<ParticipantId,Plan> plans() { return Collections.unmodifiableMap(plans); }
    public Map<ParticipantId,Breakdown> results() { return results; }
    public int heat() { return heat; }
    public int number() { return number; }
    public boolean encore() { return encore; }
    public boolean allReady() { return plans.size()==energy.size(); }
}
