package com.campus.agent.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
public class AccessTokenFilter extends OncePerRequestFilter {

    private final String accessToken;

    public AccessTokenFilter(@Value("${app.access-token}") String accessToken){
        this.accessToken = accessToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean isApi = path.startsWith("/api");
        if(!isApi){
            chain.doFilter(request, response);
            return;
        }
        String given = request.getHeader("X-Access-Token");
        if(accessToken.equals(given)){
            chain.doFilter(request, response);
            return;
        }
        else{
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"口令不正确\"}");
        }
    }
}
