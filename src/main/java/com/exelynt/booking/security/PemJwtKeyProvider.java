package com.exelynt.booking.security;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Key provider for local development: the RSA key pair comes from configuration
 * (typically {@code JWT_PRIVATE_KEY} / {@code JWT_PUBLIC_KEY} in a git-ignored
 * {@code .env}). The public key is derived from the private key when it is not
 * supplied separately.
 */
public class PemJwtKeyProvider implements JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(PemJwtKeyProvider.class);

    private final JwtSigningKey signingKey;

    public PemJwtKeyProvider(JwtProperties.Pem pem) {
        RSAPrivateKey privateKey = RsaKeys.parsePrivateKey(pem.privateKey());
        RSAPublicKey publicKey = (pem.publicKey() == null || pem.publicKey().isBlank())
                ? RsaKeys.derivePublicKey(privateKey)
                : RsaKeys.parsePublicKey(pem.publicKey());
        String keyId = (pem.keyId() == null || pem.keyId().isBlank())
                ? RsaKeys.fingerprint(publicKey)
                : pem.keyId();
        this.signingKey = new JwtSigningKey(keyId, privateKey, publicKey);
        log.info("jwt_keys_loaded source=pem keyId={}", keyId);
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
