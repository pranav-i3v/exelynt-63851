package com.exelynt.booking.security.jwt.provider;

import com.exelynt.booking.security.JwtSigningKey;

import java.security.interfaces.RSAPublicKey;
import java.util.Optional;

/**
 * Source of the RS256 key material.
 *
 * <p>The application depends only on this interface, so AWS Secrets Manager can
 * be swapped for PEM configuration (local development) or an ephemeral key pair
 * (tests) without touching the JWT service or the security filter.</p>
 */
public interface JwtKeyProvider {

    /** The key pair to sign new tokens with. */
    JwtSigningKey currentSigningKey();

    /**
     * The public key matching {@code keyId}, or empty when that key is unknown.
     *
     * <p>Implementations keep previously seen public keys so tokens signed just
     * before a rotation still verify until they expire.</p>
     */
    Optional<RSAPublicKey> verificationKey(String keyId);
}
