package io.quizmosh.server;

import io.quizmosh.application.account.AccountRepository;
import io.quizmosh.domain.account.User;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Component
public class AccountSessions {
    public static final String COOKIE = "QM_ACCOUNT";
    private final JdbcTemplate jdbc; private final AccountRepository users; private final boolean secure;
    public AccountSessions(JdbcTemplate jdbc, AccountRepository users, @Value("${quizmosh.secure-cookies:true}") boolean secure) {
        this.jdbc=jdbc; this.users=users; this.secure=secure;
    }
    public void issue(String userId,HttpServletResponse response) {
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO user_sessions VALUES (?,?,?,?)",hash(token),userId,OffsetDateTime.now(ZoneOffset.UTC),OffsetDateTime.now(ZoneOffset.UTC).plusHours(12));
        cookie(response,COOKIE,token,Duration.ofHours(12));
    }
    public Optional<User> resolve(HttpServletRequest request) {
        String token=readCookie(request,COOKIE);
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        var ids=jdbc.queryForList("SELECT user_id FROM user_sessions WHERE token_hash=? AND expires_at>?",String.class,hash(token),OffsetDateTime.now(ZoneOffset.UTC));
        return ids.isEmpty()?Optional.empty():users.find(ids.getFirst());
    }
    public void revoke(HttpServletRequest request,HttpServletResponse response) {
        String token=readCookie(request,COOKIE);
        if(token!=null) jdbc.update("DELETE FROM user_sessions WHERE token_hash=?",hash(token));
        cookie(response,COOKIE,"",Duration.ZERO);
    }
    public void cookie(HttpServletResponse response,String name,String value,Duration age) {
        response.addHeader("Set-Cookie",ResponseCookie.from(name,value).httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(age).build().toString());
    }
    public static String readCookie(HttpServletRequest request,String name) {
        if(request.getCookies()!=null) for(Cookie c:request.getCookies()) if(c.getName().equals(name)) return c.getValue();
        return null;
    }
    static String hash(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    @Scheduled(fixedDelay=3600000) public void cleanup() {jdbc.update("DELETE FROM user_sessions WHERE expires_at<?",OffsetDateTime.now(ZoneOffset.UTC));}
}
