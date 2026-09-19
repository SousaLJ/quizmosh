package io.quizmosh.server;

import io.quizmosh.application.account.AccountRepository;
import io.quizmosh.domain.account.*;
import jakarta.servlet.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ProductController {
    private final AccountRepository users; private final AccountSessions sessions; private final AccountSecurity.Providers providers;
    private final ProductPrivacy privacy; private final JdbcTemplate jdbc; private final GameService game;
    public ProductController(AccountRepository users,AccountSessions sessions,AccountSecurity.Providers providers,ProductPrivacy privacy,JdbcTemplate jdbc,GameService game) {
        this.users=users;this.sessions=sessions;this.providers=providers;this.privacy=privacy;this.jdbc=jdbc;this.game=game;
    }
    @GetMapping("/account") public Object account(Authentication auth,CsrfToken csrf,HttpServletRequest req,HttpServletResponse res) {
        var user=auth==null?null:users.find(auth.getName()).orElse(null);
        return GameService.obj("user",user,"providers",providers.entries.keySet(),"csrf",csrf.getToken(),"csrfHeader",csrf.getHeaderName(),
            "preferences",privacy.preferences(privacy.subject(req,res)));
    }
    @PostMapping("/account/logout") public Object logout(HttpServletRequest req,HttpServletResponse res) {
        sessions.revoke(req,res);return Map.of("ok",true);
    }
    @PostMapping("/account/revoke-all") public Object revokeAll(Authentication auth,HttpServletRequest req,HttpServletResponse res) {
        jdbc.update("DELETE FROM user_sessions WHERE user_id=?",auth.getName());return logout(req,res);
    }
    public record Profile(String nickname) {}
    @PostMapping("/account/profile") public Object profile(Authentication auth,@RequestBody Profile profile) {
        if(profile.nickname()==null || profile.nickname().isBlank() || profile.nickname().length()>24 || profile.nickname().codePoints().anyMatch(Character::isISOControl)) throw new ApiException(400,"error.invalid");
        jdbc.update("UPDATE user_profiles SET nickname=? WHERE user_id=?",profile.nickname().strip(),auth.getName());
        return users.find(auth.getName()).orElseThrow();
    }
    @GetMapping("/account/export") public Object export(Authentication auth) {
        String id=auth.getName();
        return GameService.obj("user",users.find(id).orElseThrow(),"identities",jdbc.queryForList("SELECT provider,subject FROM user_identities WHERE user_id=?",id),
            "preferences",privacy.preferences(id),"consents",jdbc.queryForList("SELECT consent_type,granted,policy_version,recorded_at FROM consents WHERE subject_id=?",id),
            "analytics",jdbc.queryForList("SELECT event_type,room_code,match_id,occurred_at FROM analytics_events WHERE subject_id=?",id));
    }
    public record Deletion(boolean confirm) {}
    @PostMapping("/account/delete") public Object delete(Authentication auth,@RequestBody Deletion body,HttpServletRequest req,HttpServletResponse res) {
        if(!body.confirm()) throw new ApiException(400,"error.invalid");
        privacy.eraseAccount(()->users.delete(auth.getName()));return logout(req,res);
    }
    @PostMapping("/privacy") public Object preferences(@RequestBody ProductPrivacy.Preferences body,HttpServletRequest req,HttpServletResponse res) {
        String subject=privacy.subject(req,res);
        String guest=privacy.guestSubject(req,res);
        var result=privacy.saveForBrowser(subject,guest,body);
        if(!result.analytics()) sessions.cookie(res,"QM_REF","",Duration.ZERO);
        return result;
    }
    public record Event(String event,String referral) {}
    @PostMapping("/events") public Object event(@RequestBody Event body,HttpServletRequest req,HttpServletResponse res) {
        if(body.event()==null || !ProductPrivacy.CLIENT_EVENTS.contains(body.event())) throw new ApiException(400,"error.invalid");
        String subject=privacy.subject(req,res);
        if(privacy.preferences(subject).analytics()) {
            if(privacy.validReferral(body.referral())) sessions.cookie(res,"QM_REF",body.referral(),Duration.ofDays(1));
            privacy.record(subject,body.event(),null,null,body.referral());
        }
        return Map.of("ok",true);
    }
    public record Share(String code) {}
    @PostMapping("/shares") public Object share(@RequestBody Share body,@RequestHeader(value="Authorization",required=false) String bearer,HttpServletRequest req,HttpServletResponse res) {
        if(bearer==null || !bearer.startsWith("Bearer ")) throw new ApiException(401,"error.auth");
        var identity=game.authenticate(body.code(),bearer.substring(7));
        String ref=null;
        try {ref=privacy.share(privacy.subject(req,res),identity.code());} catch(org.springframework.dao.DataAccessException ignored) { /* Sharing still works without telemetry. */ }
        return Map.of("path","/join/"+identity.code()+(ref==null?"":"?ref="+ref));
    }
    /** Called after Spring Security authorization; synchronized to make user quotas atomic. */
    public synchronized Object create(GameService.CreateRequest body,Authentication auth,HttpServletRequest req,HttpServletResponse res) {
        User user=auth==null?null:users.find(auth.getName()).orElse(null);
        if(user==null || !user.has(Entitlement.HOST_ROOM)) throw new ApiException(401,"error.loginRequired");
        var recent=jdbc.queryForList("SELECT room_code FROM host_room_creations WHERE user_id=? AND created_at>?",String.class,user.id(),OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        var active=jdbc.queryForList("SELECT room_code FROM host_room_creations WHERE user_id=?",String.class,user.id());
        if(recent.size()>=10 || active.stream().filter(game::roomExists).count()>=3) throw new ApiException(429,"error.hostLimit");
        var result=game.create(body);
        String code=(String)result.get("code");
        jdbc.update("INSERT INTO host_room_creations VALUES (?,?,?,?)",UUID.randomUUID().toString(),user.id(),code,OffsetDateTime.now(ZoneOffset.UTC));
        String subject=privacy.subject(req,res);
        game.analyticsParticipant(code,(String)result.get("playerId"),subject);
        try {
            String ref=privacy.referral(req);
            privacy.record(subject,"ROOM_CREATED",code,null,ref);
            if(ref!=null) privacy.record(subject,"ROOM_CREATED_AFTER_REFERRAL",code,null,ref);
        } catch(org.springframework.dao.DataAccessException ignored) { /* Optional telemetry. */ }
        return result;
    }
    public void joined(Map<String,Object> result,HttpServletRequest req,HttpServletResponse res) {
        try { trackJoin(result,req,res); } catch(org.springframework.dao.DataAccessException ignored) { /* Joining is independent of analytics. */ }
    }
    private void trackJoin(Map<String,Object> result,HttpServletRequest req,HttpServletResponse res) {
        String subject=privacy.subject(req,res),code=(String)result.get("code"),ref=privacy.referral(req);
        game.analyticsParticipant(code,(String)result.get("playerId"),subject);
        privacy.record(subject,"ROOM_JOINED",code,null,null);
        if(privacy.referralMatches(ref,code)) {
            privacy.record(subject,"ROOM_JOINED_FROM_SHARE",code,null,ref);
            privacy.record(subject,"SHARE_JOIN_CONVERTED",code,null,ref);
        }
    }
}
