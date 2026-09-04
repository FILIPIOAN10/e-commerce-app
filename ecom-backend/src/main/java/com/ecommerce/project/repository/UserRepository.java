package com.ecommerce.project.repository;

import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.Optional;
import java.util.List;


@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserName(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByProviderId(String providerId);

    Boolean existsByUserName(String userName);

    Boolean existsByEmail(String email);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.roleName = :role")
    Page<User> findByRoleName(@Param("role") AppRole roleSeller, Pageable pageable);

    /**
     * Everyone holding a role, unpaginated — for the small bounded sets only
     * (admins). DISTINCT because a user matching through two roles would
     * otherwise appear twice; erased tombstones are excluded because they can no
     * longer authenticate and must not be notified.
     */
    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r "
         + "WHERE r.roleName = :role AND u.erased = false")
    List<User> findAllByRoleName(@Param("role") AppRole role);
}