package com.exelynt.booking.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Key generation for tests only.
 *
 * <p>It lives in the test sources on purpose: the application itself never
 * generates key material, it only reads it from AWS Secrets Manager.</p>
 */
public final class TestRsaKeys {

    private TestRsaKeys() {
    }

    public static KeyPair generateKeyPair() {
        return generateKeyPair(2048);
    }

    public static KeyPair generateKeyPair(int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("RSA is required but unavailable in this JVM", ex);
        }
    }

    public static String privateKeyPem(KeyPair keyPair) {
        return pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
    }

    public static String publicKeyPem(KeyPair keyPair) {
        return pem("PUBLIC KEY", keyPair.getPublic().getEncoded());
    }

    public static String pem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + label + "-----\n";
    }

    /** The JSON shape the application expects inside the Secrets Manager secret. */
    public static String secretJson(KeyPair keyPair, String keyId) {
        return """
                {"privateKey":"%s","publicKey":"%s","keyId":"%s"}
                """.formatted(
                privateKeyPem(keyPair).replace("\n", "\\n"),
                publicKeyPem(keyPair).replace("\n", "\\n"),
                keyId);
    }
}
