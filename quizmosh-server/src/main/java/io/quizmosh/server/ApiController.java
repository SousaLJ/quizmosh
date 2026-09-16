package io.quizmosh.server;

import io.quizmosh.domain.common.DomainException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final GameService game;
    public ApiController(GameService game) {this.game=game;}
    @GetMapping("/meta") public Object meta() {return game.metadata();}
    @PostMapping("/rooms") public Object create(@RequestBody GameService.CreateRequest request) {return game.create(request);}
    @PostMapping("/rooms/{code}/join") public Object join(@PathVariable String code,@RequestBody GameService.JoinRequest request) {return game.join(code,request);}
    @GetMapping("/rooms/{code}") public Object state(@PathVariable String code,@RequestHeader(value="Authorization",required=false) String token) {return game.state(auth(code,token));}
    @PostMapping("/rooms/{code}/start") public Object start(@PathVariable String code,@RequestHeader(value="Authorization",required=false) String token,@RequestBody(required=false) GameService.Config config) {
        var id=auth(code,token);game.start(id,config);return game.state(id);
    }
    @PostMapping("/rooms/{code}/answer") public Object answer(@PathVariable String code,@RequestHeader(value="Authorization",required=false) String token,@RequestBody GameService.AnswerRequest request) {
        var id=auth(code,token);var receipt=game.answer(id,request);return GameService.obj("receipt",receipt,"state",game.state(id));
    }
    @PostMapping("/rooms/{code}/next") public Object next(@PathVariable String code,@RequestHeader(value="Authorization",required=false) String token) {
        var id=auth(code,token);game.next(id);return game.state(id);
    }
    @PostMapping("/rooms/{code}/plan") public Object plan(@PathVariable String code,@RequestHeader(value="Authorization",required=false) String token,@RequestBody GameService.PlanRequest request) {
        var id=auth(code,token);game.plan(id,request);return game.state(id);
    }
    @PostMapping("/rooms/{code}/leave") public Object leave(@PathVariable String code,@RequestHeader(value="Authorization",required=false) String token) {game.leave(auth(code,token));return Map.of("ok",true);}
    private GameService.Identity auth(String code,String header) {
        if(header==null || !header.startsWith("Bearer ")) throw new ApiException(401,"Entre na sala para continuar.");
        return game.authenticate(code,header.substring(7));
    }

    @RestControllerAdvice
    public static class Errors {
        @ExceptionHandler(ApiException.class) public ResponseEntity<?> api(ApiException ex) {return ResponseEntity.status(ex.status()).body(Map.of("message",ex.getMessage()));}
        @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class}) public ResponseEntity<?> invalid(Exception ex) {return ResponseEntity.badRequest().body(Map.of("message","Dados inválidos. Confira os campos e tente novamente."));}
        @ExceptionHandler(DomainException.class) public ResponseEntity<?> domain(DomainException ex) {
            String message=switch(ex.getMessage()) {
                case "nickname already in use" -> "Este apelido já está em uso na sala.";
                case "room is full" -> "A sala já tem 12 jogadores.";
                case "operation requires room owner" -> "Apenas o anfitrião pode fazer isso.";
                case "round has expired" -> "O tempo desta rodada acabou.";
                case "participant is not a player in this match" -> "Você está acompanhando esta partida como espectador.";
                default -> "Esta ação não está disponível agora. Atualize a sala e tente novamente.";
            };
            return ResponseEntity.status(ex.getMessage().equals("operation requires room owner")?403:409).body(Map.of("message",message));
        }
    }
}
