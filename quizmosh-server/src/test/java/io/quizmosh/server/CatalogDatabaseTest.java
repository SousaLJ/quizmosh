package io.quizmosh.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizmosh.domain.common.QuestionId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogDatabaseTest {
    private DriverManagerDataSource database() {
        return new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
    }
    @Test void upgradePreservesResultsAndRestartDoesNotDuplicateOrOverwriteContent() {
        var ds=database();
        Flyway.configure().dataSource(ds).target("1").load().migrate();
        var jdbc=new JdbcTemplate(ds);
        jdbc.update("INSERT INTO match_results(id,room_code,finished_at,payload) VALUES ('old-match','ABCD',CURRENT_TIMESTAMP,'{}')");
        var flyway=Flyway.configure().dataSource(ds).load();
        assertEquals(2,flyway.migrate().migrationsExecuted);
        assertEquals(600,jdbc.queryForObject("SELECT COUNT(*) FROM quiz_questions",Integer.class));
        assertEquals(1200,jdbc.queryForObject("SELECT COUNT(*) FROM quiz_question_texts",Integer.class));
        assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM quiz_categories",Integer.class));
        assertEquals(12,jdbc.queryForObject("SELECT COUNT(*) FROM quiz_category_texts",Integer.class));
        var catalog=new Catalog(jdbc,new ObjectMapper());
        assertEquals(42,catalog.inventory().size());
        for(var category:catalog.categories()) {
            assertEquals(Set.of("pt-BR","en"),category.names().keySet());
            assertEquals(100,jdbc.queryForObject("SELECT COUNT(*) FROM quiz_questions WHERE category_id=?",Integer.class,category.id()));
        }
        jdbc.update("UPDATE quiz_question_texts SET prompt='Texto revisado no banco' WHERE question_id='cinema-001' AND language='pt-BR'");
        assertEquals(0,flyway.migrate().migrationsExecuted);
        assertEquals(600,jdbc.queryForObject("SELECT COUNT(*) FROM quiz_questions",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM match_results WHERE id='old-match'",Integer.class));
        assertEquals("Texto revisado no banco",new Catalog(jdbc,new ObjectMapper()).entry(QuestionId.of("cinema-001")).prompt());
    }
    @Test void databaseControlsCategoriesAndEnabledQuestions() {
        var ds=database();Flyway.configure().dataSource(ds).load().migrate();var jdbc=new JdbcTemplate(ds);
        jdbc.update("UPDATE quiz_categories SET enabled=FALSE WHERE id='futebol'");
        jdbc.update("UPDATE quiz_questions SET enabled=FALSE WHERE id='cinema-001'");
        jdbc.update("INSERT INTO quiz_categories(id,sort_order) VALUES ('natureza',7)");
        jdbc.update("INSERT INTO quiz_category_texts(category_id,language,name) VALUES ('natureza','pt-BR','Natureza'),('natureza','en','Nature')");
        var catalog=new Catalog(jdbc,new ObjectMapper());
        assertEquals(499,catalog.size());
        assertFalse(catalog.supportsCategory("futebol"));assertTrue(catalog.supportsCategory("natureza"));
        assertNull(catalog.entry(QuestionId.of("cinema-001")));
        assertTrue(catalog.inventory().stream().anyMatch(row->row.get("category").equals("natureza")));
    }
    @Test void missingTranslationsAndMalformedContentFailStartupRatherThanFallBackToFiles() {
        var ds=database();Flyway.configure().dataSource(ds).load().migrate();var jdbc=new JdbcTemplate(ds);
        jdbc.update("DELETE FROM quiz_question_texts WHERE question_id='futebol-001' AND language='en'");
        assertTrue(assertThrows(IllegalStateException.class,()->new Catalog(jdbc,new ObjectMapper())).getMessage().contains("futebol-001"));
        jdbc.update("UPDATE quiz_questions SET enabled=FALSE WHERE id='futebol-001'");
        jdbc.update("UPDATE quiz_question_texts SET options_json='[\"one\"]' WHERE question_id='futebol-002' AND language='en'");
        assertTrue(assertThrows(IllegalStateException.class,()->new Catalog(jdbc,new ObjectMapper())).getMessage().contains("futebol-002"));
    }
}
