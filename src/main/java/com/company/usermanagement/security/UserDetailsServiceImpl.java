package com.company.usermanagement.security;

import com.company.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements Spring Security's UserDetailsService.
 *
 * Spring Security calls loadUserByUsername() during authentication
 * to fetch the user from the database.
 *
 * We use email as the "username" — it's unique and what users
 * actually provide at login.
 *
 * Our User entity already implements UserDetails, so we return
 * it directly — no wrapping needed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Called by Spring Security's auth filter to load user by email.
     *
     * @Transactional ensures the session is open while Hibernate
     * accesses any lazy-loaded fields on the User entity.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        String normalizedEmail = email == null ? null : email.trim().toLowerCase();
        log.debug("Loading user by email: {}", normalizedEmail);

        return userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("User not found with email: {}", normalizedEmail);
                    return new UsernameNotFoundException(
                            "User not found with email: " + normalizedEmail);
                });
    }
}
