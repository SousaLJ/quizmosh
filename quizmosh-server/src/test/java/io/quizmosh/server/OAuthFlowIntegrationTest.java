package io.quizmosh.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.annotation.DirtiesContext;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real authorization-code exchange against a local provider fixture, not a production login bypass. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:oauthtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE","OAUTH_GOOGLE_CLIENT_ID=test-google","OAUTH_GOOGLE_CLIENT_SECRET=test-secret","OAUTH_DISCORD_CLIENT_ID=test-discord","OAUTH_DISCORD_CLIENT_SECRET=test-secret"})
@AutoConfigureMockMvc
// Each fixture has a new issuer/key; discard Spring's cached OIDC decoder between fixtures.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OAuthFlowIntegrationTest {
    @Autowired MockMvc http; @Autowired AccountSecurity.Providers providers; @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    HttpServer server; String issuer; RSAKey key;
    AtomicReference<String> nonce=new AtomicReference<>(""); AtomicReference<String> tokenRequest=new AtomicReference<>("");
    @BeforeEach void provider() throws Exception {
        var gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);var pair=gen.generateKeyPair();
        key=new RSAKey.Builder((RSAPublicKey)pair.getPublic()).privateKey((RSAPrivateKey)pair.getPrivate()).keyID("test-key").build();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);issuer="http://127.0.0.1:"+server.getAddress().getPort();
        server.createContext("/jwks",e->{byte[] b=new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
        server.createContext("/userinfo",e->{byte[] b="{\"id\":\"discord-subject\",\"sub\":\"google-subject\"}".getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
        server.createContext("/token",e->{
            try {
                tokenRequest.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                String provider=e.getRequestURI().getPath().endsWith("google")?"google":"discord";
                Map<String,Object> response=new HashMap<>(Map.of("access_token","fixture-access","token_type","Bearer","expires_in",300));
                if(provider.equals("google")) {
                    var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),new JWTClaimsSet.Builder().issuer(issuer).subject("google-subject").audience("test-google").issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300))).claim("nonce",nonce.get()).build());
                    jwt.sign(new RSASSASigner(key));response.put("id_token",jwt.serialize());
                }
                byte[] b=json.writeValueAsBytes(response);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);
            } catch(Exception ex) {throw new RuntimeException(ex);} finally {e.close();}
        });
        server.start();
        for(String p:List.of("google","discord")) {
            var b=ClientRegistration.withRegistrationId(p).clientId("test-"+p).clientSecret("fixture-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationUri(issuer+"/authorize").tokenUri(issuer+"/token/"+p).userInfoUri(issuer+"/userinfo")
                .redirectUri("http://localhost/login/oauth2/code/"+p).scope(p.equals("google")?"openid":"identify").userNameAttributeName(p.equals("google")?"sub":"id");
            if(p.equals("google")) b.jwkSetUri(issuer+"/jwks").issuerUri(issuer);
            providers.entries.put(p,b.build());
        }
    }
    @AfterEach void stop() {server.stop(0);}
    @Test void googleRejectsSignedTokenWithWrongNonce() throws Exception {
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM users",Long.class);
        var start=http.perform(get("/oauth2/authorization/google")).andExpect(status().is3xxRedirection()).andReturn();
        Map<String,String> query=query(start.getResponse().getRedirectedUrl());nonce.set("wrong-nonce");
        http.perform(get("/login/oauth2/code/google").session((MockHttpSession)start.getRequest().getSession(false))
            .param("state",query.get("state")).param("code","fixture-code"))
            .andExpect(redirectedUrl("/?login=failed"));
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM users",Long.class));
    }
    @Test void googleOidcAndDiscordExchangeCodesValidateStateAndReuseAccounts() throws Exception {
        for(String provider:List.of("google","discord")) {
            long before=jdbc.queryForObject("SELECT COUNT(*) FROM users",Long.class);
            for(int i=0;i<2;i++) {
                var start=http.perform(get("/oauth2/authorization/"+provider)).andExpect(status().is3xxRedirection()).andReturn();
                String url=start.getResponse().getRedirectedUrl();assertNotNull(url);
                Map<String,String> query=query(url);assertTrue(query.containsKey("code_challenge"));nonce.set(query.get("nonce"));
                var session=(MockHttpSession)start.getRequest().getSession(false);
                var result=http.perform(get("/login/oauth2/code/"+provider).session(session).param("state",query.get("state")).param("code","fixture-code"))
                    .andExpect(redirectedUrl("/?login=success")).andReturn().getResponse();
                assertTrue(result.getHeaders("Set-Cookie").stream().anyMatch(s->s.startsWith("QM_ACCOUNT=") && s.contains("HttpOnly")));
                assertTrue(tokenRequest.get().contains("code_verifier="));assertTrue(session.isInvalid());
            }
            assertEquals(before+1,jdbc.queryForObject("SELECT COUNT(*) FROM users",Long.class));
        }
        http.perform(get("/login/oauth2/code/discord").param("state","forged").param("code","fixture"))
            .andExpect(redirectedUrl("/?login=failed"));
    }
    static Map<String,String> query(String url) {
        Map<String,String> map=new HashMap<>();
        for(String item:URI.create(url).getRawQuery().split("&")) {String[] pair=item.split("=",2);map.put(pair[0],URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}
        return map;
    }
}
