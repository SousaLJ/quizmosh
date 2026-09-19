package io.quizmosh.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizmosh.domain.account.UserIdentity;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:producttest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE","quizmosh.secure-cookies=true"})
@AutoConfigureMockMvc
class ProductIntegrationTest {
    @Autowired MockMvc http; @Autowired JdbcAccounts accounts; @Autowired AccountSessions sessions;
    @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json; @Autowired ProductPrivacy privacy;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    String id; Cookie cookie;
    @BeforeEach void setup() {
        id=accounts.resolve(new UserIdentity("google",UUID.randomUUID().toString())).user().id();
        var response=new MockHttpServletResponse();sessions.issue(id,response);
        String header=response.getHeader("Set-Cookie");
        assertTrue(header.contains("HttpOnly"));assertTrue(header.contains("Secure"));assertTrue(header.contains("SameSite=Lax"));
        cookie=new Cookie("QM_ACCOUNT",header.split(";",2)[0].split("=",2)[1]);
    }
    @Test void guestCannotCreateButCanJoinAndAccountRequiresCsrf() throws Exception {
        http.perform(post("/api/rooms").with(csrf()).contentType("application/json").content("{\"nickname\":\"Guest\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("error.loginRequired"));
        http.perform(post("/api/rooms").cookie(cookie).contentType("application/json").content("{\"nickname\":\"Host\"}"))
            .andExpect(status().isForbidden());
        var response=http.perform(post("/api/rooms").cookie(cookie).with(csrf()).contentType("application/json").content("{\"nickname\":\"Host\"}"))
            .andExpect(status().isOk()).andReturn().getResponse();
        String code=json.readTree(response.getContentAsString()).get("code").asText();
        http.perform(post("/api/rooms/"+code+"/join").contentType("application/json").content("{\"nickname\":\"Guest\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.state.players.length()").value(2));
    }
    @Test void loginIdentityIsStableAcrossAdaptersAndProvidersDoNotAutoMerge() {
        UserIdentity identity=new UserIdentity("discord","same-subject");
        String first=accounts.resolve(identity).user().id();
        assertEquals(first,new JdbcAccounts(jdbc,transactions).resolve(identity).user().id());
        assertNotEquals(first,accounts.resolve(new UserIdentity("google","same-subject")).user().id());
        assertFalse(accounts.resolve(identity).created());
    }
    @Test void logoutRevokesStolenCookieAndSessionExpires() throws Exception {
        http.perform(get("/api/account").cookie(cookie)).andExpect(jsonPath("$.user.id").value(id));
        http.perform(post("/api/account/logout").cookie(cookie).with(csrf())).andExpect(status().isOk());
        http.perform(get("/api/account").cookie(cookie)).andExpect(jsonPath("$.user").isEmpty());
        setup();
        jdbc.update("UPDATE user_sessions SET expires_at=? WHERE user_id=?",OffsetDateTime.now().minusSeconds(1),id);
        http.perform(get("/api/account").cookie(cookie)).andExpect(jsonPath("$.user").isEmpty());
    }
    @Test void noAnalyticsBeforeConsentAndRevocationRemovesEventsAndAttribution() throws Exception {
        http.perform(post("/api/events").cookie(cookie).with(csrf()).contentType("application/json").content("{\"event\":\"LANDING_VIEWED\"}")).andExpect(status().isOk());
        assertEquals(0,count("analytics_events"));
        privacy.save(id,new ProductPrivacy.Preferences(ProductPrivacy.VERSION,true,false,false,true));
        privacy.record(id,"ROOM_CREATED","ABCD",null,null);
        String ref=privacy.share(id,"ABCD");assertNotNull(ref);assertTrue(privacy.validReferral(ref));
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).until(()->count("analytics_events")>0);
        privacy.save(id,new ProductPrivacy.Preferences(ProductPrivacy.VERSION,false,false,false,true));
        assertEquals(0,count("analytics_events"));assertFalse(privacy.validReferral(ref));
        assertEquals(8,count("consents"));
    }
    @Test void eventAllowlistAndPolicyVersionAreEnforced() throws Exception {
        http.perform(post("/api/events").with(csrf()).contentType("application/json").content("{\"event\":\"MATCH_COMPLETED\"}"))
            .andExpect(status().isBadRequest());
        http.perform(post("/api/privacy").cookie(cookie).with(csrf()).contentType("application/json").content("{\"policyVersion\":\"old\",\"analytics\":true}"))
            .andExpect(status().isConflict());
        assertFalse(privacy.preferences(id).analytics());
    }
    @Test void rejectingAnalyticsWhileSignedInAlsoPersistsAfterLogout() throws Exception {
        Cookie browser=new Cookie("QM_PRIVACY",UUID.randomUUID().toString());
        String guest="g"+AccountSessions.hash(browser.getValue()).substring(0,32);
        privacy.save(guest,new ProductPrivacy.Preferences(ProductPrivacy.VERSION,true,false,false,true));
        privacy.save(id,new ProductPrivacy.Preferences(ProductPrivacy.VERSION,true,false,false,true));
        http.perform(post("/api/privacy").cookie(cookie,browser).with(csrf()).contentType("application/json")
            .content("{\"policyVersion\":\"2026-09-17\",\"analytics\":false}"))
            .andExpect(status().isOk());
        assertFalse(privacy.preferences(id).analytics());
        http.perform(post("/api/account/logout").cookie(cookie,browser).with(csrf())).andExpect(status().isOk());
        http.perform(get("/api/account").cookie(browser))
            .andExpect(jsonPath("$.user").isEmpty())
            .andExpect(jsonPath("$.preferences.decided").value(true))
            .andExpect(jsonPath("$.preferences.analytics").value(false));
    }
    @Test void referralsMatchRoomAndExpire() {
        privacy.save(id,new ProductPrivacy.Preferences(ProductPrivacy.VERSION,true,false,false,true));
        String ref=privacy.share(id,"ABCD");
        assertTrue(privacy.referralMatches(ref,"ABCD"));assertFalse(privacy.referralMatches(ref,"WXYZ"));
        jdbc.update("UPDATE referral_links SET expires_at=? WHERE token=?",OffsetDateTime.now().minusDays(1),ref);
        assertFalse(privacy.validReferral(ref));
    }
    @Test void forgedGuestPreferenceCookieCannotAddressAnAccount() throws Exception {
        privacy.save(id,new ProductPrivacy.Preferences(ProductPrivacy.VERSION,true,false,false,true));
        http.perform(post("/api/privacy").cookie(new Cookie("QM_PRIVACY",id)).with(csrf()).contentType("application/json")
            .content("{\"policyVersion\":\"2026-09-17\",\"analytics\":false}"))
            .andExpect(status().isOk());
        assertTrue(privacy.preferences(id).analytics());
    }
    @Test void guestShareDoesNotRequireAccountAndNoConsentProducesUntrackedLink() throws Exception {
        var host=json.readTree(http.perform(post("/api/rooms").cookie(cookie).with(csrf()).contentType("application/json").content("{\"nickname\":\"Host\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String code=host.get("code").asText();
        var guest=json.readTree(http.perform(post("/api/rooms/"+code+"/join").contentType("application/json").content("{\"nickname\":\"Guest\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        http.perform(post("/api/shares").with(csrf()).header("Authorization","Bearer "+guest.get("token").asText()).contentType("application/json").content("{\"code\":\""+code+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.path").value("/join/"+code));
    }
    @Test void exportAndDeleteAreScopedAndAllSessionsDisappear() throws Exception {
        http.perform(get("/api/account/export")).andExpect(status().isUnauthorized());
        http.perform(get("/api/account/export").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(id)).andExpect(jsonPath("$.token").doesNotExist());
        http.perform(post("/api/account/delete").cookie(cookie).with(csrf()).contentType("application/json").content("{\"confirm\":true}")).andExpect(status().isOk());
        assertTrue(accounts.find(id).isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_sessions WHERE user_id=?",Integer.class,id));
    }
    @Test void roomQuotaAndRevokingAllSessionsWork() throws Exception {
        for(int i=0;i<3;i++)http.perform(post("/api/rooms").cookie(cookie).with(csrf()).contentType("application/json").content("{\"nickname\":\"Host\"}")).andExpect(status().isOk());
        http.perform(post("/api/rooms").cookie(cookie).with(csrf()).contentType("application/json").content("{\"nickname\":\"Host\"}")).andExpect(status().isTooManyRequests());
        http.perform(post("/api/account/revoke-all").cookie(cookie).with(csrf())).andExpect(status().isOk());
        http.perform(get("/api/account").cookie(cookie)).andExpect(jsonPath("$.user").isEmpty());
    }
    int count(String table) {return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE subject_id=?",Integer.class,id);}
}
