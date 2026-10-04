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
 * MongoDB document representing an uploaded user resume.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = AppConstants.COLLECTION_RESUME_DOCUMENT)
public class ResumeDocument {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String filename;
    private String contentType;
    private byte[] data;
    private long size;
    private boolean active;
    private Instant uploadedAt;
}
