package io.quizmosh.server;

import io.quizmosh.application.account.AccountRepository;
import io.quizmosh.domain.account.UserIdentity;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;

@Configuration
public class AccountSecurity {
    /** Empty registry is valid: providers appear only when BOTH credentials are configured. */
    public static class Providers implements ClientRegistrationRepository, Iterable<ClientRegistration> {
        final Map<String,ClientRegistration> entries=new LinkedHashMap<>();
        public ClientRegistration findByRegistrationId(String id) {return entries.get(id);}
        public Iterator<ClientRegistration> iterator() {return entries.values().iterator();}
    }
    @Bean Providers providers(Environment env) {
        Providers result=new Providers();
        for(String provider:List.of("google","discord")) {
            String prefix="OAUTH_"+provider.toUpperCase(Locale.ROOT);
            String id=env.getProperty(prefix+"_CLIENT_ID",""), secret=env.getProperty(prefix+"_CLIENT_SECRET","");
            if(id.isBlank() || secret.isBlank()) continue;
            String base=env.getProperty("PUBLIC_BASE_URL","http://localhost:8080").replaceAll("/$","");
            var b=ClientRegistration.withRegistrationId(provider).clientId(id).clientSecret(secret)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .redirectUri(base+"/login/oauth2/code/"+provider).clientName(provider);
            if(provider.equals("google")) b.scope("openid").authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token").jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .issuerUri("https://accounts.google.com").userInfoUri("https://openidconnect.googleapis.com/v1/userinfo").userNameAttributeName("sub");
            else b.scope("identify").authorizationUri("https://discord.com/oauth2/authorize")
                .tokenUri("https://discord.com/api/oauth2/token").userInfoUri("https://discord.com/api/users/@me").userNameAttributeName("id");
            result.entries.put(provider,b.build());
        }
        return result;
    }
    @Bean SecurityFilterChain security(HttpSecurity http,Providers providers,AccountSessions sessions,AccountRepository users,ProductPrivacy privacy,Environment env) throws Exception {
        var csrf=new CookieCsrfTokenRepository();
        csrf.setCookieCustomizer(c->c.httpOnly(true).secure(env.getProperty("quizmosh.secure-cookies",Boolean.class,true)).sameSite("Lax").path("/"));
        http.securityContext(c->c.securityContextRepository(new NullSecurityContextRepository()))
            .headers(h->h.cacheControl(c->c.disable()))
            .requestCache(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable())
            .csrf(c->c.csrfTokenRepository(csrf).ignoringRequestMatchers(request -> {
                String p=request.getRequestURI();
                // Room participation is authorized ONLY by a bearer token, never an account cookie.
                return p.matches("/api/rooms/[^/]+/(join|start|answer|next|plan|leave)");
            }))
            .authorizeHttpRequests(a->a.requestMatchers(org.springframework.http.HttpMethod.POST,"/api/rooms").authenticated()
                .requestMatchers("/api/account/export","/api/account/delete","/api/account/profile","/api/account/revoke-all").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->error(req,res,401,"error.loginRequired"))
                .accessDeniedHandler((req,res,ex)->error(req,res,403,"error.csrf")))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
                    var user=sessions.resolve(req);
                    user.ifPresent(u->SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u.id(),null,AuthorityUtils.createAuthorityList("ROLE_USER"))));
                    chain.doFilter(req,res);
                }
            },AnonymousAuthenticationFilter.class);
        if(!providers.entries.isEmpty()) {
            var resolver=new DefaultOAuth2AuthorizationRequestResolver(providers,"/oauth2/authorization");
            resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
            http.oauth2Login(o->o.clientRegistrationRepository(providers)
                .authorizedClientRepository(new OAuth2AuthorizedClientRepository() {
                    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String id,org.springframework.security.core.Authentication a,HttpServletRequest r) {return null;}
                    public void saveAuthorizedClient(OAuth2AuthorizedClient c,org.springframework.security.core.Authentication a,HttpServletRequest r,HttpServletResponse s) {}
                    public void removeAuthorizedClient(String id,org.springframework.security.core.Authentication a,HttpServletRequest r,HttpServletResponse s) {}
                })
                .authorizationEndpoint(a->a.authorizationRequestResolver(resolver))
                .successHandler((req,res,auth)->{
                    var oauth=(OAuth2AuthenticationToken)auth;
                    OAuth2User principal=oauth.getPrincipal();
                    var account=users.resolve(new UserIdentity(oauth.getAuthorizedClientRegistrationId(),principal.getName()));
                    String oldSubject=privacy.subject(req,res);
                    privacy.transferChoice(oldSubject,account.user().id());
                    privacy.record(account.user().id(),"LOGIN_COMPLETED",null,null,privacy.referral(req));
                    if(account.created()) privacy.record(account.user().id(),"ACCOUNT_CREATED",null,null,privacy.referral(req));
                    if(account.created() && privacy.referral(req)!=null) privacy.record(account.user().id(),"ACCOUNT_CREATED_FROM_SHARE",null,null,privacy.referral(req));
                    sessions.revoke(req,res); sessions.issue(account.user().id(),res);
                    if(req.getSession(false)!=null) req.getSession(false).invalidate();
                    csrf.saveToken(null,req,res);
                    SecurityContextHolder.clearContext();
                    res.sendRedirect("/?login=success");
                }).failureHandler((req,res,ex)->{
                    if(req.getSession(false)!=null) req.getSession(false).invalidate();
                    res.sendRedirect("/?login=failed");
                }));
        }
        return http.build();
    }
    static void error(HttpServletRequest req,HttpServletResponse res,int status,String key) throws IOException {
        res.setStatus(status);res.setContentType("application/json");res.setCharacterEncoding("UTF-8");
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(res.getWriter(),Messages.body(key,req.getLocale(),Map.of()));
    }
}
