package io.quizmosh.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Game tests use the same SQL migrations and database reader as a real installation. */
final class CatalogTestSupport {
    private static final class Seeded {
        static final Catalog CATALOG=load();
        private static Catalog load() {
            var ds=new DriverManagerDataSource("jdbc:h2:mem:game_catalog;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
            Flyway.configure().dataSource(ds).load().migrate();
            return new Catalog(new JdbcTemplate(ds),new ObjectMapper());
        }
    }
    static Catalog catalog() {return Seeded.CATALOG;}
}
