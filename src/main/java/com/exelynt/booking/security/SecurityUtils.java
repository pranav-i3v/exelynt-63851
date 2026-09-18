package com.exelynt.booking.security;

import com.exelynt.booking.common.exception.UnauthorizedException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Read-only access to the authenticated caller. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AppUserPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AppUserPrincipal principal
                ? Optional.of(principal)
                : Optional.empty();
    }

    /** The caller, or 401 if the request somehow reached a secured path unauthenticated. */
    public static AppUserPrincipal requireCurrentPrincipal() {
        return currentPrincipal()
                .orElseThrow(() -> new UnauthorizedException("Authentication is required to access this resource"));
    }

    public static Optional<String> currentUsername() {
        return currentPrincipal().map(AppUserPrincipal::getUsername);
    }

    public static Optional<JwtTokenDetails> currentTokenDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        return authentication.getDetails() instanceof JwtTokenDetails details
                ? Optional.of(details)
                : Optional.empty();
    }
}
