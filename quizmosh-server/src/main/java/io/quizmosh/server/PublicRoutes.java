package io.quizmosh.server;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
@Controller
public class PublicRoutes {
    @GetMapping({"/privacy","/terms","/cookies","/how-to-play"})
    public String page(jakarta.servlet.http.HttpServletRequest request) {return "forward:"+request.getRequestURI()+"/index.html";}
    @GetMapping("/join/{code:[A-Za-z0-9]{4,8}}")
    public String join() {return "forward:/index.html";}
}
