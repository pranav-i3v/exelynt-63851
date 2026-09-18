package com.exelynt.booking.security.jwt.provider.impl;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;

import com.exelynt.booking.security.JwtSigningKey;
import com.exelynt.booking.security.RsaKeys;
import com.exelynt.booking.security.jwt.provider.JwtKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a throwaway RSA key pair in memory at startup.
 *
 * <p>For tests and throwaway sandboxes only: the key never leaves the process,
 * so every restart invalidates all outstanding tokens and a second instance
 * cannot verify the first one's tokens. Selecting this source outside a test
 * profile is logged as a warning.</p>
 */
public class GeneratedJwtKeyProvider implements JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(GeneratedJwtKeyProvider.class);

    private final JwtSigningKey signingKey;

    public GeneratedJwtKeyProvider() {
        KeyPair keyPair = RsaKeys.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        this.signingKey = new JwtSigningKey(
                RsaKeys.fingerprint(publicKey), (RSAPrivateKey) keyPair.getPrivate(), publicKey);
        log.warn("jwt_keys_generated keyId={} - ephemeral in-memory key pair, not for production use",
                signingKey.keyId());
    }

    @Override
    public JwtSigningKey currentSigningKey() {
        return signingKey;
    }

    @Override
    public Optional<RSAPublicKey> verificationKey(String keyId) {
        return signingKey.keyId().equals(keyId) ? Optional.of(signingKey.publicKey()) : Optional.empty();
    }
}
