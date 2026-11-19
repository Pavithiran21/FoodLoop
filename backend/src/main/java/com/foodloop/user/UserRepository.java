package com.foodloop.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * SELECT ... FOR UPDATE: two Admins suspending (or a person deleting themselves) at once take turns.
     * Plain SQL: Hibernate's lock mode would add "OF <alias>", which TiDB refuses.
     */
    @Query(value = "select * from users where id = :id for update", nativeQuery = true)
    Optional<User> lockById(Long id);
}
