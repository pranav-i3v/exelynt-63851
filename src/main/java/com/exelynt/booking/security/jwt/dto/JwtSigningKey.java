package com.exelynt.booking.security.jwt.dto;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * The RSA key pair currently used to sign access tokens, identified by a key id
 * that is published in the JWT header ({@code kid}) so verifiers can pick the
 * right public key after a rotation.
 */
public record JwtSigningKey(String keyId, RSAPrivateKey privateKey, RSAPublicKey publicKey) {
}
