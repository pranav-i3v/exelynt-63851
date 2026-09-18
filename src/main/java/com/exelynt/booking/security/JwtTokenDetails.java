package com.exelynt.booking.security;

import java.time.Instant;

/**
 * Details attached to the current {@code Authentication}, so logout can revoke
 * exactly the access token that was presented.
 */
public record JwtTokenDetails(String jti, Instant expiresAt) {
}
