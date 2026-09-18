package com.exelynt.booking.security;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * PEM parsing and key derivation for the RS256 signing keys.
 *
 * <p>Plain JCE only: no BouncyCastle, no extra dependency to keep current.
 * There is deliberately no key-generation helper here — the application reads
 * keys from AWS Secrets Manager and never creates one.</p>
 */
public final class RsaKeys {

    private static final String ALGORITHM = "RSA";

    private RsaKeys() {
    }

    /** Parses a PKCS#8 PEM private key ({@code -----BEGIN PRIVATE KEY-----}). */
    public static RSAPrivateKey parsePrivateKey(String pem) {
        String normalised = requireText(pem, "private key");
        if (normalised.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalStateException("""
                    The private key is in PKCS#1 format, which the JDK cannot read directly. \
                    Convert it once with: \
                    openssl pkcs8 -topk8 -nocrypt -in key.pem -out key-pkcs8.pem""");
        }
        if (normalised.contains("ENCRYPTED PRIVATE KEY")) {
            throw new IllegalStateException(
                    "The private key is passphrase-encrypted; store it unencrypted in the secret store instead");
        }
        byte[] der = decodeBody(normalised, "PRIVATE KEY");
        try {
            var key = KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(key instanceof RSAPrivateKey rsaKey)) {
                throw new IllegalStateException("The configured private key is not an RSA key");
            }
            requireStrongEnough(rsaKey.getModulus());
            return rsaKey;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("The private key could not be parsed as a PKCS#8 RSA key", ex);
        }
    }

    /** Parses an X.509 PEM public key ({@code -----BEGIN PUBLIC KEY-----}). */
    public static RSAPublicKey parsePublicKey(String pem) {
        byte[] der = decodeBody(requireText(pem, "public key"), "PUBLIC KEY");
        try {
            var key = KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(der));
            if (!(key instanceof RSAPublicKey rsaKey)) {
                throw new IllegalStateException("The configured public key is not an RSA key");
            }
            return rsaKey;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("The public key could not be parsed as an X.509 RSA key", ex);
        }
    }

    /**
     * Derives the public key from a PKCS#8 private key, so the secret only has
     * to carry one value. Requires the usual CRT form, which {@code openssl
     * genrsa} and {@code openssl genpkey} both produce.
     */
    public static RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) {
        if (!(privateKey instanceof RSAPrivateCrtKey crtKey)) {
            throw new IllegalStateException(
                    "The public key cannot be derived from this private key; store it alongside the private key");
        }
        try {
            return (RSAPublicKey) KeyFactory.getInstance(ALGORITHM)
                    .generatePublic(new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("The public key could not be derived from the private key", ex);
        }
    }

    /**
     * A stable key id derived from the public key itself, used when the secret
     * does not carry one. The same key always yields the same id, so tokens stay
     * verifiable across restarts.
     */
    public static String fingerprint(RSAPublicKey publicKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(publicKey.getModulus().toByteArray());
            digest.update(publicKey.getPublicExponent().toByteArray());
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
            return encoded.substring(0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable in this JVM", ex);
        }
    }

    private static void requireStrongEnough(BigInteger modulus) {
        int bits = modulus.bitLength();
        if (bits < JwtProperties.MIN_KEY_SIZE_BITS) {
            throw new IllegalStateException("The RSA signing key must be at least "
                    + JwtProperties.MIN_KEY_SIZE_BITS + " bits, but is " + bits + " bits");
        }
    }

    private static String requireText(String pem, String what) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("No " + what + " was supplied");
        }
        // Secret stores and environment variables often carry literal "\n" escapes.
        return pem.replace("\\n", "\n").trim();
    }

    private static byte[] decodeBody(String pem, String label) {
        String body = pem
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s", "");
        if (body.isEmpty()) {
            throw new IllegalStateException("The PEM block for the " + label.toLowerCase() + " is empty");
        }
        try {
            return Base64.getDecoder().decode(body.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("The " + label.toLowerCase() + " is not valid base64 PEM content", ex);
        }
    }
}
