package io.quizmosh.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:httptest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE","spring.datasource.username=sa"})
@AutoConfigureMockMvc
class HttpIntegrationTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired ResultArchive archive;
    @Autowired JdbcTemplate jdbc;
    @Test void guestHttpFlowRequiresBearerAndHidesSecrets() throws Exception {
        var response=http.perform(post("/api/rooms").contentType("application/json").content("{\"nickname\":\"Teste\",\"practice\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state.phase").value("LOBBY"))
                .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
        var value=json.readTree(response.getContentAsString());String code=value.get("code").asText(),token=value.get("token").asText();
        assertTrue(token.length()>=40);
        http.perform(get("/api/rooms/"+code)).andExpect(status().isUnauthorized());
        http.perform(get("/api/rooms/"+code).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.players.length()").value(4)).andExpect(jsonPath("$.token").doesNotExist());
        http.perform(post("/api/rooms/"+code+"/start").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.phase").value("COUNTDOWN"));
        http.perform(post("/api/rooms/"+code+"/leave").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        http.perform(get("/api/rooms/"+code).header("Authorization","Bearer "+token)).andExpect(status().is4xxClientError());
    }
    @Test void malformedPayloadAndForeignOriginsAreRejected() throws Exception {
        http.perform(post("/api/rooms").contentType("application/json").content("{\"nickname\":\"\"}")).andExpect(status().isBadRequest());
        http.perform(post("/api/rooms").contentType("application/json").content("not json")).andExpect(status().isBadRequest());
        http.perform(post("/api/rooms").header("Origin","https://evil.example").header("Host","localhost").contentType("application/json").content("{\"nickname\":\"x\"}")).andExpect(status().isForbidden());
        http.perform(post("/api/rooms").contentType("application/json").content("x".repeat(5000))).andExpect(status().isPayloadTooLarge());
    }
    @Test void migrationsAndResultPersistenceAreIdempotent() throws Exception {
        archive.save("test-match","ABCD",java.util.Map.of("ranking",java.util.List.of()));
        archive.save("test-match","ABCD",java.util.Map.of("ranking",java.util.List.of()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM match_results WHERE id='test-match'",Integer.class));
    }
}
