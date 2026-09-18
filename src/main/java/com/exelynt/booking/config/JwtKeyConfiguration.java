package com.exelynt.booking.config;

import com.exelynt.booking.security.AwsSecretsManagerJwtKeyProvider;
import com.exelynt.booking.security.GeneratedJwtKeyProvider;
import com.exelynt.booking.security.JwtKeyProvider;
import com.exelynt.booking.security.JwtProperties;
import com.exelynt.booking.security.PemJwtKeyProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Chooses where the RS256 key pair comes from, based on {@code app.jwt.key-source}.
 *
 * <p>The AWS client is only created for the {@code aws-secrets-manager} source,
 * so local runs and tests never touch the AWS SDK's credential chain.</p>
 */
@Configuration
public class JwtKeyConfiguration {

    /**
     * Region is read from configuration when set, otherwise from the standard AWS
     * chain ({@code AWS_REGION}, profile, instance metadata). Credentials always
     * come from the default provider chain — an IAM role in production.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.jwt.key-source", havingValue = "aws-secrets-manager", matchIfMissing = true)
    public SecretsManagerClient secretsManagerClient(JwtProperties properties) {
        var builder = SecretsManagerClient.builder();
        String region = properties.aws().region();
        if (region != null && !region.isBlank() && !region.startsWith("${")) {
            builder.region(Region.of(region));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.jwt.key-source", havingValue = "aws-secrets-manager", matchIfMissing = true)
    public JwtKeyProvider awsSecretsManagerJwtKeyProvider(SecretsManagerClient secretsManagerClient,
                                                          ObjectMapper objectMapper,
                                                          JwtProperties properties) {
        return new AwsSecretsManagerJwtKeyProvider(secretsManagerClient, objectMapper, properties.aws());
    }

    @Bean
    @ConditionalOnProperty(name = "app.jwt.key-source", havingValue = "pem")
    public JwtKeyProvider pemJwtKeyProvider(JwtProperties properties) {
        return new PemJwtKeyProvider(properties.pem());
    }

    @Bean
    @ConditionalOnProperty(name = "app.jwt.key-source", havingValue = "generated")
    public JwtKeyProvider generatedJwtKeyProvider() {
        return new GeneratedJwtKeyProvider();
    }
}
