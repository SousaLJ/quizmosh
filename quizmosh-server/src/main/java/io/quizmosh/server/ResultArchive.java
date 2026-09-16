package io.quizmosh.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class ResultArchive {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public ResultArchive(JdbcTemplate jdbc, ObjectMapper json) {this.jdbc=jdbc;this.json=json;}
    public void save(String id,String roomCode,Object result) throws Exception {
        // Idempotent retry, including after a commit whose acknowledgement was lost.
        if(jdbc.queryForObject("SELECT COUNT(*) FROM match_results WHERE id=?",Integer.class,id)==0)
            jdbc.update("INSERT INTO match_results(id,room_code,finished_at,payload) VALUES (?,?,?,?)",
                    id,roomCode,OffsetDateTime.ofInstant(Instant.now(),ZoneOffset.UTC),json.writeValueAsString(result));
    }
}
