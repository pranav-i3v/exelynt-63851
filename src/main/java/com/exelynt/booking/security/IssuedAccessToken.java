package com.exelynt.booking.security;

import java.time.Instant;

/** A freshly minted access token together with the metadata callers need. */
public record IssuedAccessToken(String token, String jti, Instant expiresAt, long expiresInSeconds) {
}
