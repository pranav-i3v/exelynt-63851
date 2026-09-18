package com.exelynt.booking.config;

import com.exelynt.booking.security.config.JwtProperties;
import com.exelynt.booking.security.jwt.provider.JwtKeyProvider;
import com.exelynt.booking.security.jwt.provider.impl.AwsSecretsManagerJwtKeyProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the one and only source of RS256 key material: AWS Secrets Manager.
 *
 * <p>There is deliberately no alternative provider. Key material cannot be
 * supplied through configuration, an environment variable or a file, and the
 * application never generates a key pair — if the secret cannot be read, it
 * does not start.</p>
 */
@Configuration
public class JwtKeyConfiguration {

    /**
     * Region is read from configuration when set, otherwise from the standard AWS
     * chain ({@code AWS_REGION}, profile, instance metadata). Credentials always
     * come from the default provider chain — an IAM role in production.
     */
    @Bean(destroyMethod = "close")
    public SecretsManagerClient secretsManagerClient(JwtProperties properties) {
        var builder = SecretsManagerClient.builder();
        String region = properties.aws().region();
        if (region != null && !region.isBlank() && !region.startsWith("${")) {
            builder.region(Region.of(region));
        }
        return builder.build();
    }

    @Bean
    public JwtKeyProvider jwtKeyProvider(SecretsManagerClient secretsManagerClient,
                                         ObjectMapper objectMapper,
                                         JwtProperties properties) {
        return new AwsSecretsManagerJwtKeyProvider(secretsManagerClient, objectMapper, properties.aws());
    }
}
