package com.company.usermanagement.repository;

import com.company.usermanagement.config.TestConfig;
import com.company.usermanagement.config.TestcontainersConfig;
import com.company.usermanagement.entity.User;
import com.company.usermanagement.entity.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // Use external DB (Testcontainers)
@Import({TestcontainersConfig.class, TestConfig.class}) // Import Testcontainers and tenant test config
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager; // Used to flush changes and clear cache

    private static final String TEST_EMAIL_PREFIX = "testuser";
    private static final String ADMIN_EMAIL_PREFIX = "adminuser";

    private User createTestUser(String email, Role role, boolean enabled) {
        return User.builder()
                .firstName("Test")
                .lastName("User")
                .email(email)
                .password("hashedpassword") // Dummy password, not used in this test context
                .role(role)
                .enabled(enabled)
                .accountNonLocked(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private User createUniqueAdminUser(int index) {
        return createTestUser(ADMIN_EMAIL_PREFIX + index + "@example.com", Role.ROLE_ADMIN, true);
    }

    private User createUniqueRegularUser(int index) {
        return createTestUser(TEST_EMAIL_PREFIX + index + "@example.com", Role.ROLE_USER, true);
    }

    @Test
    void saveAndFindUserByEmail_ShouldSucceed() {
        User newUser = createUniqueRegularUser(1);
        User savedUser = userRepository.save(newUser);
        entityManager.flush(); // Ensure changes are written to DB

        Optional<User> foundUser = userRepository.findByEmail(savedUser.getEmail());

        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getId()).isEqualTo(savedUser.getId());
        assertThat(foundUser.get().getEmail()).isEqualTo(savedUser.getEmail());
        assertThat(foundUser.get().getFirstName()).isEqualTo("Test");
    }

    @Test
    void existsByEmail_ShouldReturnTrueForExistingUserAndFalseForNewUser() {
        User existingUser = createUniqueRegularUser(2);
        userRepository.save(existingUser);
        entityManager.flush();

        boolean existsExisting = userRepository.existsByEmail(existingUser.getEmail());
        boolean existsNew = userRepository.existsByEmail("nonexistent@example.com");

        assertThat(existsExisting).isTrue();
        assertThat(existsNew).isFalse();
    }

    @Test
    void findByEmail_ShouldReturnEmptyForNonExistentEmail() {
        Optional<User> foundUser = userRepository.findByEmail("nonexistent@example.com");
        assertThat(foundUser).isNotPresent();
    }

    @Test
    void saveUser_WithDuplicateEmail_ShouldThrowDataIntegrityViolationException() {
        User user1 = createUniqueRegularUser(3);
        userRepository.save(user1);
        entityManager.flush();

        User user2 = createUniqueRegularUser(3); // Same email as user1
        user2.setEmail(user1.getEmail()); // Ensure email is identical

        assertThatThrownBy(() -> {
            userRepository.save(user2);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("idx_users_email_lower");
    }

    @Test
    void deleteUserById_ShouldSucceed() {
        User user = createUniqueRegularUser(4);
        User savedUser = userRepository.save(user);
        entityManager.flush();

        userRepository.deleteById(savedUser.getId());
        entityManager.flush();

        Optional<User> foundUser = userRepository.findById(savedUser.getId());
        assertThat(foundUser).isNotPresent();
    }

    @Test
    void findAllByRole_ShouldReturnUsersOfSpecificRole() {
        User adminUser1 = createUniqueAdminUser(1);
        User adminUser2 = createUniqueAdminUser(2);
        User regularUser = createUniqueRegularUser(5);

        userRepository.saveAll(List.of(adminUser1, adminUser2, regularUser));
        entityManager.flush();
        entityManager.clear(); // Clear cache to ensure fresh data retrieval

        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").ascending());
        var adminsPage = userRepository.findAllByRole(Role.ROLE_ADMIN, pageable);
        var regularUsersPage = userRepository.findAllByRole(Role.ROLE_USER, pageable);

        assertThat(adminsPage.getTotalElements()).isEqualTo(2);
        assertThat(adminsPage.getContent()).extracting(User::getEmail).containsExactlyInAnyOrder(
                adminUser1.getEmail(), adminUser2.getEmail());

        assertThat(regularUsersPage.getTotalElements()).isEqualTo(1);
        assertThat(regularUsersPage.getContent()).extracting(User::getEmail).containsExactly(regularUser.getEmail());
    }
    
    @Test
    void findAllByEnabled_ShouldReturnCorrectUsers() {
        User enabledUser1 = createUniqueRegularUser(6);
        User enabledUser2 = createUniqueRegularUser(7);
        User disabledUser = createUniqueRegularUser(8);
        disabledUser.setEnabled(false);

        userRepository.saveAll(List.of(enabledUser1, enabledUser2, disabledUser));
        entityManager.flush();
        entityManager.clear();

        Pageable pageable = PageRequest.of(0, 10);
        var enabledUsers = userRepository.findAllByEnabled(true, pageable);
        var disabledUsers = userRepository.findAllByEnabled(false, pageable);

        assertThat(enabledUsers.getTotalElements()).isEqualTo(2);
        assertThat(enabledUsers.getContent()).extracting(User::getEmail).containsExactlyInAnyOrder(
                enabledUser1.getEmail(), enabledUser2.getEmail());

        assertThat(disabledUsers.getTotalElements()).isEqualTo(1);
        assertThat(disabledUsers.getContent()).extracting(User::getEmail).containsExactly(disabledUser.getEmail());
    }

    // Test for soft delete/enabled flag behavior:
    // The User entity has an 'enabled' boolean field, not a soft-delete marker.
    // This test verifies that disabling a user makes them not visible in "enabled" queries
    // and that the 'enabled' status can be updated.
    @Test
    void userEnabledFlag_ShouldControlVisibilityAndStatus() {
        User user = createUniqueRegularUser(9);
        user.setEnabled(true); // Initially enabled
        userRepository.save(user);
        entityManager.flush();

        // Verify user is found when enabled
        Pageable pageable = PageRequest.of(0, 10);
        var enabledUsersInitially = userRepository.findAllByEnabled(true, pageable);
        assertThat(enabledUsersInitially.getContent()).anyMatch(u -> u.getId().equals(user.getId()));

        // Disable the user
        // We need to fetch the user again to modify and save, or use TestEntityManager
        User fetchedUser = userRepository.findById(user.getId()).orElseThrow();
        fetchedUser.setEnabled(false);
        userRepository.save(fetchedUser);
        entityManager.flush();

        // Verify user is NOT found when enabled, but IS found when disabled
        var enabledUsersAfterDisable = userRepository.findAllByEnabled(true, pageable);
        assertThat(enabledUsersAfterDisable.getContent()).noneMatch(u -> u.getId().equals(user.getId()));

        var disabledUsers = userRepository.findAllByEnabled(false, pageable);
        assertThat(disabledUsers.getTotalElements()).isEqualTo(1);
        assertThat(disabledUsers.getContent()).anyMatch(u -> u.getId().equals(user.getId()));
    }
    
    @Test
    void searchByKeyword_ShouldFindUsersInNameOrEmail() {
        User user1 = createTestUser("john.doe@example.com", Role.ROLE_USER, true);
        user1.setFirstName("John");
        user1.setLastName("Doe");
        User user2 = createTestUser("jane.smith@example.com", Role.ROLE_USER, true);
        user2.setFirstName("Jane");
        user2.setLastName("Smith");
        User user3 = createTestUser("peter.jones@example.com", Role.ROLE_USER, true);
        user3.setFirstName("Peter");
        user3.setLastName("Jones");
        User user4 = createTestUser("alice.wonderland@example.com", Role.ROLE_USER, true);
        user4.setFirstName("Alice");
        user4.setLastName("Doe");

        userRepository.saveAll(List.of(user1, user2, user3, user4));
        entityManager.flush();
        entityManager.clear();

        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").ascending());

        // Search by first name
        var resultsFirstName = userRepository.searchByKeyword("John", pageable);
        assertThat(resultsFirstName).hasSize(1).extracting(User::getEmail).containsExactly("john.doe@example.com");

        // Search by last name
        var resultsLastName = userRepository.searchByKeyword("Smith", pageable);
        assertThat(resultsLastName).hasSize(1).extracting(User::getEmail).containsExactly("jane.smith@example.com");
        
        // Search by email (partial match)
        var resultsEmail = userRepository.searchByKeyword("wonderland", pageable);
        assertThat(resultsEmail).hasSize(1).extracting(User::getEmail).containsExactly("alice.wonderland@example.com");

        // Search by keyword matching across fields (case-insensitive)
        var resultsCaseInsensitive = userRepository.searchByKeyword("DOE", pageable);
        assertThat(resultsCaseInsensitive).hasSize(2).extracting(User::getEmail).containsExactlyInAnyOrder("john.doe@example.com", "alice.wonderland@example.com");
    }
}
