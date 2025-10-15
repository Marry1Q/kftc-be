package com.kftc_backend.domain.user.repository;

import com.kftc_backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    Optional<User> findByUserCi(String userCi);
    
    boolean existsByUserCi(String userCi);
} 