package com.exelynt.booking.security.filter;

import com.exelynt.booking.common.logging.MdcUser;
import com.exelynt.booking.common.web.ApiErrorWriter;
import com.exelynt.booking.security.jwt.dto.JwtTokenDetails;
import com.exelynt.booking.security.blacklist.TokenBlacklist;
import com.exelynt.booking.security.jwt.service.JwtService;
import com.exelynt.booking.security.user.AppUserPrincipal;
import com.exelynt.booking.security.user.service.AppUserDetailsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <jwt>}.
 *
 * <p>Expired, malformed, wrongly signed and blacklisted tokens are all rejected
 * with a JSON 401 built from the standard error shape. Requests without the
 * header pass through untouched: the entry point decides whether the endpoint
 * needed authentication.</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;
    private final TokenBlacklist tokenBlacklist;
    private final ApiErrorWriter errorWriter;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   AppUserDetailsService userDetailsService,
                                   TokenBlacklist tokenBlacklist,
                                   ApiErrorWriter errorWriter) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklist = tokenBlacklist;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        Claims claims;
        try {
            claims = jwtService.parse(token).getPayload();
        } catch (ExpiredJwtException ex) {
            // The token itself is never logged, only the fact that it expired.
            log.debug("Rejected an expired access token");
            reject(request, response, "Access token has expired");
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected an invalid access token: {}", ex.getClass().getSimpleName());
            reject(request, response, "Access token is invalid");
            return;
        }

        String jti = jwtService.extractJti(claims);
        if (tokenBlacklist.isBlacklisted(jti)) {
            log.debug("Rejected a blacklisted access token");
            reject(request, response, "Access token has been revoked");
            return;
        }

        AppUserPrincipal principal;
        try {
            principal = userDetailsService.loadUserByUsername(jwtService.extractUsername(claims));
        } catch (UsernameNotFoundException ex) {
            reject(request, response, "Access token is invalid");
            return;
        }
        if (!principal.isEnabled()) {
            reject(request, response, "Account is disabled");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        authentication.setDetails(new JwtTokenDetails(jti, jwtService.extractExpiry(claims)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MdcUser.set(principal.getUsername());
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            MdcUser.clear();
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, message);
    }
}
