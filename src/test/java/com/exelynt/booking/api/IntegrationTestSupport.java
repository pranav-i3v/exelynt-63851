package com.exelynt.booking.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.exelynt.booking.security.JwtKeyProvider;
import com.exelynt.booking.security.JwtProperties;
import com.exelynt.booking.security.JwtService;
import com.exelynt.booking.security.RsaKeys;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared plumbing for the MockMvc suites: a real application context on H2,
 * plus helpers to log the seeded accounts in and to forge tokens.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestSupport {

    protected static final String ADMIN_USERNAME = "admin";
    protected static final String ADMIN_PASSWORD = "Admin@123";
    protected static final String USER_USERNAME = "user";
    protected static final String USER_PASSWORD = "User@123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtProperties jwtProperties;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected JwtKeyProvider jwtKeyProvider;

    protected String adminAccessToken;
    protected String userAccessToken;

    @BeforeEach
    void authenticateSeededUsers() throws Exception {
        adminAccessToken = accessToken(ADMIN_USERNAME, ADMIN_PASSWORD);
        userAccessToken = accessToken(USER_USERNAME, USER_PASSWORD);
    }

    protected String login(String username, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    protected String accessToken(String username, String password) throws Exception {
        return JsonPath.read(login(username, password), "$.accessToken");
    }

    protected String refreshToken(String username, String password) throws Exception {
        return JsonPath.read(login(username, password), "$.refreshToken");
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    /** Builds a correctly signed token, with the real signing key, that expired two minutes ago. */
    protected String expiredAccessToken(String username) {
        Instant expiredAt = Instant.now().minusSeconds(120);
        return Jwts.builder()
                .header().keyId(jwtKeyProvider.currentSigningKey().keyId()).and()
                .issuer(jwtProperties.issuer())
                .subject(username)
                .id(UUID.randomUUID().toString())
                .claim(JwtService.CLAIM_USER_ID, 1L)
                .claim(JwtService.CLAIM_ROLE, "ADMIN")
                .issuedAt(Date.from(expiredAt.minusSeconds(900)))
                .expiration(Date.from(expiredAt))
                .signWith(jwtKeyProvider.currentSigningKey().privateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /** A structurally valid token signed with a different RSA key, under our key id. */
    protected String foreignlySignedAccessToken(String username) {
        KeyPair attackerKeyPair = RsaKeys.generateKeyPair();
        return Jwts.builder()
                .header().keyId(jwtKeyProvider.currentSigningKey().keyId()).and()
                .issuer(jwtProperties.issuer())
                .subject(username)
                .id(UUID.randomUUID().toString())
                .claim(JwtService.CLAIM_USER_ID, 1L)
                .claim(JwtService.CLAIM_ROLE, "ADMIN")
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(attackerKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    /** A token signed with a key the application has never seen, under an unknown key id. */
    protected String unknownKeyIdAccessToken(String username) {
        KeyPair attackerKeyPair = RsaKeys.generateKeyPair();
        return Jwts.builder()
                .header().keyId("a-key-we-never-issued").and()
                .issuer(jwtProperties.issuer())
                .subject(username)
                .id(UUID.randomUUID().toString())
                .claim(JwtService.CLAIM_USER_ID, 1L)
                .claim(JwtService.CLAIM_ROLE, "ADMIN")
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(attackerKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }
}
