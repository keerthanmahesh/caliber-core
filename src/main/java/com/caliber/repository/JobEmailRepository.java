package com.caliber.repository;

import com.caliber.dto.JobStatsDto;
import com.caliber.model.ApplicationStatus;
import com.caliber.model.EmploymentType;
import com.caliber.model.JobEmail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface JobEmailRepository extends MongoRepository<JobEmail, String> {

    Optional<JobEmail> findByThreadId(String threadId);

    boolean existsByMessageId(String messageId);

    boolean existsByUserIdAndMessageId(String userId, String messageId);

    Optional<JobEmail> findByUserIdAndThreadId(String userId, String threadId);

    Optional<JobEmail> findFirstByUserIdAndSenderEmailAndSubjectIgnoreCase(String userId, String senderEmail, String subject);

    Optional<JobEmail> findFirstBySenderEmailAndSubjectIgnoreCase(String senderEmail, String subject);

    Page<JobEmail> findByEmploymentType(EmploymentType employmentType, Pageable pageable);

    @Query("{ 'employmentType': ?0, 'applicationStatus': { $nin: ?1 } }")
    Page<JobEmail> findByEmploymentTypeAndApplicationStatusNotIn(EmploymentType employmentType, Collection<ApplicationStatus> excludedStatuses, Pageable pageable);

    @Query("{ $or: [ { 'employmentType': 'UNSPECIFIED' }, { 'employmentType': null } ], 'applicationStatus': { $nin: ?0 } }")
    Page<JobEmail> findUnspecifiedAndApplicationStatusNotIn(Collection<ApplicationStatus> excludedStatuses, Pageable pageable);

    @Query("{ 'applicationStatus': ?0 }")
    Page<JobEmail> findByApplicationStatus(ApplicationStatus applicationStatus, Pageable pageable);

    @Query("{ 'applicationStatus': { $in: ?0 } }")
    Page<JobEmail> findByApplicationStatusIn(Collection<ApplicationStatus> statuses, Pageable pageable);

    @Aggregation(pipeline = {
            "{ $group: { " +
            "    _id: null, " +
            "    total: { $sum: 1 }, " +
            "    c2c: { $sum: { $cond: [{ $and: [{ $eq: ['$employmentType', 'C2C'] }, { $or: [{ $eq: ['$applicationStatus', 'PENDING'] }, { $eq: [{ $ifNull: ['$applicationStatus', null] }, null] }] }] }, 1, 0] } }, " +
            "    c2h: { $sum: { $cond: [{ $and: [{ $eq: ['$employmentType', 'C2H'] }, { $or: [{ $eq: ['$applicationStatus', 'PENDING'] }, { $eq: [{ $ifNull: ['$applicationStatus', null] }, null] }] }] }, 1, 0] } }, " +
            "    w2: { $sum: { $cond: [{ $and: [{ $eq: ['$employmentType', 'W2'] }, { $or: [{ $eq: ['$applicationStatus', 'PENDING'] }, { $eq: [{ $ifNull: ['$applicationStatus', null] }, null] }] }] }, 1, 0] } }, " +
            "    fullTime: { $sum: { $cond: [{ $and: [{ $eq: ['$employmentType', 'FULL_TIME'] }, { $or: [{ $eq: ['$applicationStatus', 'PENDING'] }, { $eq: [{ $ifNull: ['$applicationStatus', null] }, null] }] }] }, 1, 0] } }, " +
            "    unspecified: { $sum: { $cond: [{ $and: [{ $or: [{ $eq: ['$employmentType', 'UNSPECIFIED'] }, { $eq: [{ $ifNull: ['$employmentType', null] }, null] }] }, { $or: [{ $eq: ['$applicationStatus', 'PENDING'] }, { $eq: [{ $ifNull: ['$applicationStatus', null] }, null] }] }] }, 1, 0] } }, " +
            "    confirmedC2c: { $sum: { $cond: [{ $and: [{ $eq: ['$employmentType', 'C2C'] }, { $or: [{ $eq: ['$applicationStatus', 'PENDING'] }, { $eq: [{ $ifNull: ['$applicationStatus', null] }, null] }] }] }, 1, 0] } }, " +
            "    pending: { $sum: { $cond: [{ $or: [{ $eq: ['$applicationStatus', 'PENDING'] }, { $eq: [{ $ifNull: ['$applicationStatus', null] }, null] }] }, 1, 0] } }, " +
            "    inquired: { $sum: { $cond: [{ $eq: ['$applicationStatus', 'INQUIRED'] }, 1, 0] } }, " +
            "    applied: { $sum: { $cond: [{ $eq: ['$applicationStatus', 'APPLIED'] }, 1, 0] } }, " +
            "    dismissed: { $sum: { $cond: [{ $eq: ['$applicationStatus', 'DISMISSED'] }, 1, 0] } } " +
            "} }"
    })
    Optional<JobStatsDto> getAggregatedStats();

    @Query("{ $or: [ " +
            "{ 'jobTitle': { $regex: ?0, $options: 'i' } }, " +
            "{ 'clientOrCompany': { $regex: ?0, $options: 'i' } }, " +
            "{ 'senderName': { $regex: ?0, $options: 'i' } }, " +
            "{ 'senderEmail': { $regex: ?0, $options: 'i' } }, " +
            "{ 'primarySkills': { $regex: ?0, $options: 'i' } }, " +
            "{ 'subject': { $regex: ?0, $options: 'i' } } " +
            "] }")
    Page<JobEmail> searchAll(String keyword, Pageable pageable);
}
