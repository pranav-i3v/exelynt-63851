package com.exelynt.booking.config;

import com.exelynt.booking.security.SecurityUtils;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Fills {@code createdBy} / {@code updatedBy} from the authenticated caller. */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    /** Startup work (seeding, for instance) runs without a principal and is attributed to "system". */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(SecurityUtils.currentUsername().orElse("system"));
    }
}
