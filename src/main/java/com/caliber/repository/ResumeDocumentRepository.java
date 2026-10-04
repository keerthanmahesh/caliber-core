package com.caliber.repository;

import com.caliber.model.ResumeDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeDocumentRepository extends MongoRepository<ResumeDocument, String> {

    Optional<ResumeDocument> findByUserIdAndActiveTrue(String userId);

    List<ResumeDocument> findByUserIdOrderByUploadedAtDesc(String userId);

    Optional<ResumeDocument> findFirstByActiveTrue();

    Optional<ResumeDocument> findFirstByOrderByUploadedAtDesc();
}
