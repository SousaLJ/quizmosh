package io.quizmosh.server;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

@Component
public final class RequestGuard extends OncePerRequestFilter {
    private record Window(long minute,int count) {}
    private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        response.setHeader("X-Content-Type-Options","nosniff");
        response.setHeader("Referrer-Policy","no-referrer");
        response.setHeader("X-Frame-Options","DENY");
        response.setHeader("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; media-src 'self'; connect-src 'self' ws: wss:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
        if(request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control","no-store");
            if(request.getContentLengthLong()>4096) {reject(response,413,"Solicitação muito grande.");return;}
            String origin=request.getHeader("Origin");
            if(origin!=null) {
                try {
                    if(!URI.create(origin).getRawAuthority().equalsIgnoreCase(request.getHeader("Host"))) {reject(response,403,"Origem não autorizada.");return;}
                } catch(Exception e) {reject(response,403,"Origem inválida.");return;}
            }
            long minute=System.currentTimeMillis()/60000;
            if(windows.size()>10000) windows.entrySet().removeIf(e->e.getValue().minute()<minute);
            // Identity limits are unaffected by a shared Wi-Fi or reverse proxy.
            String token=request.getHeader("Authorization");
            boolean anonymous=token==null;
            String key=anonymous?request.getRemoteAddr():token;
            if(key.length()>160) {reject(response,401,"Sessão inválida.");return;}
            if(windows.size()>12000 && !windows.containsKey(key)) {reject(response,429,"Tente novamente em instantes.");return;}
            var window=windows.compute(key,(k,v)->new Window(minute,v!=null&&v.minute()==minute?v.count()+1:1));
            if(window.count()>(anonymous?180:240)) {response.setHeader("Retry-After","60");reject(response,429,"Muitas tentativas. Aguarde um minuto.");return;}
        }
        chain.doFilter(request,response);
    }
    private void reject(HttpServletResponse response,int code,String message) throws IOException {
        response.setStatus(code);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\""+message+"\"}");
    }
}
