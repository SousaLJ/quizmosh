package io.quizmosh.server;

import io.quizmosh.application.account.AccountRepository;
import io.quizmosh.domain.account.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Repository
public class JdbcAccounts implements AccountRepository {
    private final JdbcTemplate jdbc;
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    public JdbcAccounts(JdbcTemplate jdbc,org.springframework.transaction.PlatformTransactionManager manager) { this.jdbc = jdbc; this.transactions=new org.springframework.transaction.support.TransactionTemplate(manager); }
    @Override public synchronized Resolution resolve(UserIdentity identity) {
        return transactions.execute(status->resolveInTransaction(identity));
    }
    private Resolution resolveInTransaction(UserIdentity identity) {
        var ids = jdbc.queryForList("SELECT user_id FROM user_identities WHERE provider=? AND subject=?", String.class, identity.provider(), identity.subject());
        if (!ids.isEmpty()) return new Resolution(find(ids.getFirst()).orElseThrow(), false);
        String id = UUID.randomUUID().toString(); Instant now = Instant.now();
        jdbc.update("INSERT INTO users VALUES (?,?)", id, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
        jdbc.update("INSERT INTO user_profiles VALUES (?,?)", id, "Player");
        jdbc.update("INSERT INTO user_identities VALUES (?,?,?)", identity.provider(), identity.subject(), id);
        return new Resolution(new User(id, "Player", now), true);
    }
    @Override public Optional<User> find(String id) {
        return jdbc.query("SELECT u.id,p.nickname,u.created_at FROM users u JOIN user_profiles p ON p.user_id=u.id WHERE u.id=?",
            (rs,n) -> new User(rs.getString(1),rs.getString(2),rs.getObject(3,OffsetDateTime.class).toInstant()),id).stream().findFirst();
    }
    @Override @Transactional public void delete(String id) {
        for (String table : List.of("analytics_events","referral_links","consents","privacy_preferences"))
            jdbc.update("DELETE FROM " + table + " WHERE subject_id=?", id);
        jdbc.update("DELETE FROM users WHERE id=?",id);
    }
}
