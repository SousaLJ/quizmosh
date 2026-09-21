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
    @Test void metadataPublishesCategoriesAndCountsWithoutQuestionAnswers() throws Exception {
        var response=http.perform(get("/api/meta")).andExpect(status().isOk())
            .andExpect(jsonPath("$.questions").value(600))
            .andExpect(jsonPath("$.categories.length()").value(6))
            .andExpect(jsonPath("$.catalog.length()").value(42))
            .andExpect(jsonPath("$.categories[2].id").value("futebol"))
            .andExpect(jsonPath("$.categories[2].names.en").value("Football (soccer)"))
            .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("correctIndex"));assertFalse(response.contains("answers"));
        assertFalse(response.contains("prompt"));assertFalse(response.contains("numeric_value"));
        http.perform(get("/questions.json")).andExpect(status().isNotFound());
        http.perform(get("/questions.en.json")).andExpect(status().isNotFound());
    }
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
    @Test void errorsFollowRequestLanguageButRoomContentUsesItsOwnSetting() throws Exception {
        http.perform(get("/api/rooms/ABCD").header("Accept-Language","en-US"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("error.auth"))
            .andExpect(jsonPath("$.message").value(Messages.text("error.auth",java.util.Locale.ENGLISH,java.util.Map.of())));
        http.perform(get("/api/rooms/ABCD").header("Accept-Language","pt-BR"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(Messages.text("error.auth",java.util.Locale.forLanguageTag("pt-BR"),java.util.Map.of())));
        http.perform(post("/api/rooms").header("Accept-Language","en").contentType("application/json").content("not-json"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("error.invalid"));
        http.perform(post("/api/rooms").header("Accept-Language","en").header("Origin","https://evil.example").header("Host","localhost").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(Messages.text("error.origin",java.util.Locale.ENGLISH,java.util.Map.of())));
        var cfg=new GameService.Config(8,25,"all",java.util.List.of("classic-trivia","quick-fire","guess-it","closest-wins"),true,"pt-BR","REGIONAL","BR");
        http.perform(post("/api/rooms").header("Accept-Language","en").contentType("application/json").content(json.writeValueAsString(new GameService.CreateRequest("English UI",true,cfg))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.state.config.questionLanguage").value("pt-BR"))
            .andExpect(jsonPath("$.state.config.contentScope").value("REGIONAL"));
        var insufficient=new GameService.Config(12,25,"cinema",java.util.List.of("guess-it"),true,"en","REGIONAL","BR");
        http.perform(post("/api/rooms").header("Accept-Language","en").contentType("application/json").content(json.writeValueAsString(new GameService.CreateRequest("Blocked",true,insufficient))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("error.catalogCapacity"))
            .andExpect(jsonPath("$.arguments.available").value(8)).andExpect(jsonPath("$.arguments.required").value(12));
    }
}
