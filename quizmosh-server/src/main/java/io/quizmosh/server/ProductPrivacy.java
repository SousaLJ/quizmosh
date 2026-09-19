package io.quizmosh.server;

import io.quizmosh.application.account.AnalyticsPort;
import jakarta.servlet.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;

@Service
public class ProductPrivacy implements AnalyticsPort {
    public static final String VERSION="2026-09-17";
    public record Preferences(String policyVersion,boolean analytics,boolean advertising,boolean personalization,boolean decided) {}
    public static final Set<String> CLIENT_EVENTS=Set.of("LANDING_VIEWED","JOIN_GAME_CLICKED","CREATE_GAME_CLICKED","LOGIN_STARTED","SHARE_CREATED","SHARE_OPENED");
    private final JdbcTemplate jdbc; private final AccountSessions sessions;
    private final TransactionTemplate transactions;
    private final java.util.concurrent.ThreadPoolExecutor writer=new java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,
        new java.util.concurrent.ArrayBlockingQueue<>(256),r->{Thread t=new Thread(r,"product-analytics");t.setDaemon(true);return t;},new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
    public ProductPrivacy(JdbcTemplate jdbc,AccountSessions sessions,PlatformTransactionManager manager) {this.jdbc=jdbc;this.sessions=sessions;this.transactions=new TransactionTemplate(manager);}
    public String subject(HttpServletRequest req,HttpServletResponse res) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.getPrincipal() instanceof String id && id.matches("[a-f0-9-]{36}")) return id;
        return guestSubject(req,res);
    }
    public String guestSubject(HttpServletRequest req,HttpServletResponse res) {
        if(req.getAttribute("quizmosh.privacy-subject") instanceof String known) return known;
        String id=AccountSessions.readCookie(req,"QM_PRIVACY");
        if(id==null || !id.matches("[a-f0-9-]{36}")) {id=UUID.randomUUID().toString();sessions.cookie(res,"QM_PRIVACY",id,Duration.ofDays(180));}
        // Separate guest capability namespace: a forged privacy cookie cannot address a known account ID.
        String subject="g"+AccountSessions.hash(id).substring(0,32);
        req.setAttribute("quizmosh.privacy-subject",subject);
        return subject;
    }
    public Preferences preferences(String subject) {
        var values=jdbc.query("SELECT policy_version,analytics,advertising,personalization FROM privacy_preferences WHERE subject_id=? AND updated_at>?",
            (rs,n)->new Preferences(rs.getString(1),rs.getBoolean(2),rs.getBoolean(3),rs.getBoolean(4),true),subject,OffsetDateTime.now(ZoneOffset.UTC).minusDays(180));
        if(values.isEmpty() || !VERSION.equals(values.getFirst().policyVersion())) return new Preferences(VERSION,false,false,false,false);
        return values.getFirst();
    }
    public synchronized Preferences save(String subject,Preferences choice) {
        // Commit while holding the same lock as the writer: revocation cannot race a queued event.
        return transactions.execute(status->saveChoice(subject,choice));
    }
    public synchronized Preferences saveForBrowser(String subject,String guest,Preferences choice) {
        return transactions.execute(status->{
            Preferences result=saveChoice(subject,choice);
            if(!guest.equals(subject)) saveChoice(guest,choice);
            return result;
        });
    }
    private Preferences saveChoice(String subject,Preferences choice) {
        if(!VERSION.equals(choice.policyVersion())) throw new ApiException(409,"error.policyChanged");
        var now=OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("DELETE FROM privacy_preferences WHERE subject_id=?",subject);
        jdbc.update("INSERT INTO privacy_preferences VALUES (?,?,?,?,?,?)",subject,VERSION,choice.analytics(),choice.advertising(),choice.personalization(),now);
        Map<String,Boolean> choices=Map.of("necessary",true,"analytics",choice.analytics(),"advertising",choice.advertising(),"personalization",choice.personalization());
        choices.forEach((type,granted)->jdbc.update("INSERT INTO consents VALUES (?,?,?,?,?,?)",UUID.randomUUID().toString(),subject,type,granted,VERSION,now));
        if(!choice.analytics()) {
            jdbc.update("DELETE FROM analytics_events WHERE subject_id=?",subject);
            jdbc.update("DELETE FROM referral_links WHERE subject_id=?",subject);
        }
        return preferences(subject);
    }
    public synchronized void transferChoice(String from,String to) {
        Preferences choice=preferences(from);
        // The explicit browser choice wins, including rejection; no implicit consent on account creation.
        if(choice.decided()) save(to,choice);
    }
    public synchronized void eraseAccount(Runnable deletion) {deletion.run();}
    @Override public void record(String subject,String event,String roomCode,String matchId,String referral) {
        writer.execute(()->write(subject,event,roomCode,matchId,referral));
    }
    private synchronized void write(String subject,String event,String roomCode,String matchId,String referral) {
        try {
            if(subject==null || !preferences(subject).analytics()) return;
            jdbc.update("INSERT INTO analytics_events VALUES (?,?,?,?,?,?,?)",UUID.randomUUID().toString(),subject,event,roomCode,matchId,validReferral(referral)?referral:null,OffsetDateTime.now(ZoneOffset.UTC));
        } catch(org.springframework.dao.DataAccessException ignored) {
            // Product telemetry must never prevent login or gameplay. No payload/PII logging.
        }
    }
    @jakarta.annotation.PreDestroy public void shutdown() {writer.shutdownNow();}
    public synchronized String share(String subject,String room) {
        if(!preferences(subject).analytics()) return null;
        String token=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO referral_links VALUES (?,?,?,?,?)",token,subject,room,OffsetDateTime.now(ZoneOffset.UTC),OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        record(subject,"SHARE_CREATED",room,null,token);return token;
    }
    public boolean validReferral(String token) {
        return token!=null && token.matches("[a-f0-9-]{36}") && jdbc.queryForObject("SELECT COUNT(*) FROM referral_links WHERE token=? AND expires_at>?",Integer.class,token,OffsetDateTime.now(ZoneOffset.UTC))>0;
    }
    public boolean referralMatches(String token,String room) {
        return validReferral(token) && jdbc.queryForObject("SELECT COUNT(*) FROM referral_links WHERE token=? AND room_code=?",Integer.class,token,room)>0;
    }
    public String referral(HttpServletRequest req) {
        String token=AccountSessions.readCookie(req,"QM_REF");return validReferral(token)?token:null;
    }
    @Scheduled(fixedDelay=3600000) public void cleanup() {
        var now=OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("DELETE FROM analytics_events WHERE occurred_at<?",now.minusDays(90));
        jdbc.update("DELETE FROM referral_links WHERE expires_at<?",now);
        jdbc.update("DELETE FROM consents WHERE recorded_at<?",now.minusDays(365));
        jdbc.update("DELETE FROM privacy_preferences WHERE updated_at<?",now.minusDays(180));
        jdbc.update("DELETE FROM host_room_creations WHERE created_at<?",now.minusDays(1));
        jdbc.update("DELETE FROM match_results WHERE finished_at<?",now.minusDays(30));
    }
}
