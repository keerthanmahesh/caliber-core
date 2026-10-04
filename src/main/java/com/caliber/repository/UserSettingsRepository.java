package com.caliber.repository;

import com.caliber.model.UserSettings;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSettingsRepository extends MongoRepository<UserSettings, String> {

    Optional<UserSettings> findByUserId(String userId);

    List<UserSettings> findByGmailConnectedTrue();
}
