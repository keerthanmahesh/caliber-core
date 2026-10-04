package com.caliber.model;

import com.caliber.constant.AppConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document representing a registered user in Caliber,
 * provisioned just-in-time from authentication claims.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = AppConstants.COLLECTION_USER)
public class User {
    @Id
    private String id;

    @Indexed(unique = true)
    private String sub;

    @Indexed(unique = true)
    private String email;

    @Indexed(unique = true)
    private String username;

    private String firstName;
    private String lastName;
    private String picture;
    private AuthProvider authProvider;

    @Builder.Default
    private Instant lastLoginAt = Instant.now();

    private Long createdTime;
    private Long lastUpdatedTime;
    private String createdBy;
    private String lastUpdatedBy;
}
