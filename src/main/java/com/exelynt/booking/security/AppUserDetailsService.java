package com.exelynt.booking.security;

import com.exelynt.booking.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AppUserPrincipal loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(AppUserPrincipal::from)
                // The message stays generic: it must not reveal whether the account exists.
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));
    }
}
