package com.caliber.service;

import com.caliber.model.ApplicationStatus;
import com.caliber.model.EmploymentType;
import com.caliber.model.JobEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ClassificationEngineTest {

    @Mock
    private AiClientService aiClientService;

    private ClassificationEngine classificationEngine;

    @BeforeEach
    void setUp() {
        classificationEngine = new ClassificationEngine(aiClientService);
    }

    @Test
    void testExplicitC2CPositiveRegex() {
        JobEmail email = JobEmail.builder()
                .subject("Senior Java Developer - C2C Only")
                .bodyText("Hi, We have a role open for Java Developer on Corp-to-Corp. Rate is $85/hr.")
                .snippet("role open for Java Developer on Corp-to-Corp")
                .build();

        classificationEngine.classifyAndEnrich(email, "user-123");

        assertEquals(EmploymentType.C2C, email.getEmploymentType());
        assertEquals(ApplicationStatus.PENDING, email.getApplicationStatus());
        assertEquals("$85/hr", email.getRate());
    }

    @Test
    void testExplicitW2OnlyNegativeRegex() {
        JobEmail email = JobEmail.builder()
                .subject("Full Stack Lead - W2 Only")
                .bodyText("Immediate requirement for W2 position only. No C2C allowed. Rate: $70/hr.")
                .snippet("W2 position only. No C2C allowed.")
                .build();

        classificationEngine.classifyAndEnrich(email, "user-123");

        assertEquals(EmploymentType.W2, email.getEmploymentType());
    }

    @Test
    void testUnspecifiedStatusWhenNoTaxTerms() {
        JobEmail email = JobEmail.builder()
                .subject("Senior Python Engineer Opening")
                .bodyText("Hi, I saw your profile and wanted to reach out regarding a Senior Python Engineer opening. Are you open for opportunities?")
                .snippet("reaching out regarding a Senior Python Engineer opening")
                .build();

        classificationEngine.classifyAndEnrich(email, "user-123");

        assertEquals(EmploymentType.UNSPECIFIED, email.getEmploymentType());
    }

    @Test
    void testContractToHireRegex() {
        JobEmail email = JobEmail.builder()
                .subject("DevOps Engineer - Contract to Hire")
                .bodyText("This is a 6 month contract-to-hire position located in Dallas, TX. Hybrid work.")
                .snippet("6 month contract-to-hire position")
                .build();

        classificationEngine.classifyAndEnrich(email, "user-123");

        assertEquals(EmploymentType.C2H, email.getEmploymentType());
        assertEquals("Hybrid", email.getLocationType());
    }

    @Test
    void testBypassLlmWhenAlreadyTagged() {
        JobEmail email = JobEmail.builder()
                .subject("Senior Java Architect")
                .bodyText("Looking for a Java Architect. Rate is $100/hr.")
                .employmentType(EmploymentType.C2C)
                .applicationStatus(ApplicationStatus.INQUIRED)
                .build();

        classificationEngine.classifyAndEnrich(email, "user-123");

        // Should preserve already tagged status and bypass LLM completely
        assertEquals(EmploymentType.C2C, email.getEmploymentType());
        assertEquals(ApplicationStatus.INQUIRED, email.getApplicationStatus());
        assertEquals("$100/hr", email.getRate());
        org.mockito.Mockito.verifyNoInteractions(aiClientService);
    }
}
