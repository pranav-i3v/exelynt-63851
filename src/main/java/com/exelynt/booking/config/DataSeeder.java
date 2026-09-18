package com.exelynt.booking.config;

import com.exelynt.booking.resource.entity.Resource;
import com.exelynt.booking.resource.repository.ResourceRepository;
import com.exelynt.booking.resource.common.ResourceType;
import com.exelynt.booking.user.common.Role;
import com.exelynt.booking.user.entity.User;
import com.exelynt.booking.user.repository.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the two demo accounts and three sample resources on first start.
 *
 * <p>The passwords below are development seeds documented in the README; they
 * are the only credentials in the code base and are meant to be changed before
 * any deployment that matters.</p>
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_SEED_PASSWORD = "Admin@123";
    private static final String USER_USERNAME = "user";
    private static final String USER_SEED_PASSWORD = "User@123";

    private final UserRepository userRepository;
    private final ResourceRepository resourceRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      ResourceRepository resourceRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.resourceRepository = resourceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUser(ADMIN_USERNAME, ADMIN_SEED_PASSWORD, Role.ADMIN);
        seedUser(USER_USERNAME, USER_SEED_PASSWORD, Role.USER);
        seedResources();
    }

    private void seedUser(String username, String rawPassword, Role role) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        userRepository.save(new User(username, passwordEncoder.encode(rawPassword), role));
        log.info("seeded_user username={} role={}", username, role);
    }

    private void seedResources() {
        if (resourceRepository.count() > 0) {
            return;
        }
        List<Resource> samples = List.of(
                new Resource("Meeting Room Alpha", ResourceType.ROOM,
                        "Eight-seat meeting room with a projector", true),
                new Resource("Company Van", ResourceType.VEHICLE,
                        "Nine-seat van, requires a category B licence", true),
                new Resource("4K Projector", ResourceType.EQUIPMENT,
                        "Portable 4K projector with an HDMI and a USB-C cable", true));
        resourceRepository.saveAll(samples);
        log.info("seeded_resources count={}", samples.size());
    }
}
