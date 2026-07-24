package com.medchain.repository;

import com.medchain.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPseudonymId(String pseudonymId);
    List<User> findByRole(String role);
    List<User> findByBlocked(boolean blocked);
    boolean existsByEmail(String email);
}
