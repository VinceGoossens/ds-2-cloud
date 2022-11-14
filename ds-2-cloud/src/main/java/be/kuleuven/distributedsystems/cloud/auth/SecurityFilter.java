package be.kuleuven.distributedsystems.cloud.auth;

import be.kuleuven.distributedsystems.cloud.entities.User;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // TODO: (level 1) decode Identity Token and assign correct email and role
        // TODO: (level 2) verify Identity Token
        Base64.Decoder decoder = Base64.getUrlDecoder();
        String payloadString = new String(decoder.decode(request.getHeader(HttpHeaders.AUTHORIZATION).split("\\.")[1]));

        JSONParser parser = new JSONParser();
        JSONObject payload = null;
        try {
            payload = (JSONObject) parser.parse(payloadString);
            String role = null;
            if (payload.get("role") != null) {
                role = payload.get("role").toString();
            }
            var user = new User(payload.get("email").toString(), role);
            SecurityContext context = SecurityContextHolder.getContext();
            context.setAuthentication(new FirebaseAuthentication(user));
            filterChain.doFilter(request, response);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api");
    }

    private static class FirebaseAuthentication implements Authentication {
        private final User user;

        FirebaseAuthentication(User user) {
            this.user = user;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            if (user.isManager()) {
                return List.of(new SimpleGrantedAuthority("manager"));
            } else {
                return new ArrayList<>();
            }
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getDetails() {
            return null;
        }

        @Override
        public User getPrincipal() {
            return this.user;
        }

        @Override
        public boolean isAuthenticated() {
            return true;
        }

        @Override
        public void setAuthenticated(boolean b) throws IllegalArgumentException {

        }

        @Override
        public String getName() {
            return null;
        }
    }
}

