package com.caliber.repository;

import com.caliber.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for {@link User} entity.
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {

    @Query("{ 'email': ?0 }")
    Optional<User> findByEmail(String email);

    @Query("{ 'sub': ?0 }")
    Optional<User> findBySub(String sub);
}
