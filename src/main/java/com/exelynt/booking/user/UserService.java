package com.exelynt.booking.user;

import com.exelynt.booking.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to accounts for the other modules; keeps them off {@code UserRepository}. */
@Service
public class UserService {

    private static final String ENTITY_TYPE = "User";

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of(ENTITY_TYPE, id));
    }

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User '" + username + "' was not found"));
    }
}
