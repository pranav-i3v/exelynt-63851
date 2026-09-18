package com.exelynt.booking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import com.exelynt.booking.security.common.RsaKeys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RsaKeysTest {

    private static KeyPair keyPair;

    @BeforeAll
    static void generate() {
        keyPair = TestRsaKeys.generateKeyPair();
    }

    @Test
    @DisplayName("a PKCS#8 PEM private key round-trips")
    void parsesPrivateKey() {
        RSAPrivateKey parsed = RsaKeys.parsePrivateKey(privateKeyPem());

        assertThat(parsed.getModulus()).isEqualTo(((RSAPrivateKey) keyPair.getPrivate()).getModulus());
    }

    @Test
    @DisplayName("an X.509 PEM public key round-trips")
    void parsesPublicKey() {
        RSAPublicKey parsed = RsaKeys.parsePublicKey(publicKeyPem());

        assertThat(parsed.getModulus()).isEqualTo(((RSAPublicKey) keyPair.getPublic()).getModulus());
    }

    @Test
    @DisplayName("literal \\n escapes, as secret stores hand them back, are tolerated")
    void toleratesEscapedNewlines() {
        String escaped = privateKeyPem().replace("\n", "\\n");

        assertThat(RsaKeys.parsePrivateKey(escaped).getModulus())
                .isEqualTo(((RSAPrivateKey) keyPair.getPrivate()).getModulus());
    }

    @Test
    @DisplayName("the public key can be derived from the private key alone")
    void derivesPublicKey() {
        RSAPublicKey derived = RsaKeys.derivePublicKey((RSAPrivateKey) keyPair.getPrivate());

        assertThat(derived.getModulus()).isEqualTo(((RSAPublicKey) keyPair.getPublic()).getModulus());
        assertThat(derived.getPublicExponent()).isEqualTo(((RSAPublicKey) keyPair.getPublic()).getPublicExponent());
    }

    @Test
    @DisplayName("the fingerprint is stable for a key and differs between keys")
    void fingerprintIsStable() {
        String first = RsaKeys.fingerprint((RSAPublicKey) keyPair.getPublic());

        assertThat(first).hasSize(16);
        assertThat(RsaKeys.fingerprint((RSAPublicKey) keyPair.getPublic())).isEqualTo(first);
        assertThat(RsaKeys.fingerprint((RSAPublicKey) TestRsaKeys.generateKeyPair().getPublic())).isNotEqualTo(first);
    }

    @Test
    @DisplayName("a PKCS#1 key is rejected with the conversion command")
    void rejectsPkcs1WithGuidance() {
        String pkcs1 = "-----BEGIN RSA PRIVATE KEY-----\nMIICXQ==\n-----END RSA PRIVATE KEY-----";

        assertThatThrownBy(() -> RsaKeys.parsePrivateKey(pkcs1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openssl pkcs8 -topk8");
    }

    @Test
    @DisplayName("an encrypted private key is rejected")
    void rejectsEncryptedKey() {
        String encrypted = "-----BEGIN ENCRYPTED PRIVATE KEY-----\nMIICXQ==\n-----END ENCRYPTED PRIVATE KEY-----";

        assertThatThrownBy(() -> RsaKeys.parsePrivateKey(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("passphrase-encrypted");
    }

    @Test
    @DisplayName("a missing or unparseable key is rejected")
    void rejectsGarbage() {
        assertThatThrownBy(() -> RsaKeys.parsePrivateKey(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> RsaKeys.parsePrivateKey("-----BEGIN PRIVATE KEY-----\n-----END PRIVATE KEY-----"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> RsaKeys.parsePrivateKey("-----BEGIN PRIVATE KEY-----\nnot base64 !!\n-----END PRIVATE KEY-----"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("an RSA key below 2048 bits is rejected")
    void rejectsWeakKey() {
        KeyPair weak = TestRsaKeys.generateKeyPair(1024);
        String pem = pem("PRIVATE KEY", weak.getPrivate().getEncoded());

        assertThatThrownBy(() -> RsaKeys.parsePrivateKey(pem))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 2048 bits");
    }

    private static String privateKeyPem() {
        return pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
    }

    private static String publicKeyPem() {
        return pem("PUBLIC KEY", keyPair.getPublic().getEncoded());
    }

    private static String pem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + label + "-----\n";
    }
}
