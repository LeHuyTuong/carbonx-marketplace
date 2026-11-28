package com.carbonx.mrv.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** JPA row of {@code project_applications}. */
@Entity
@Table(name = "project_applications")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectApplicationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "company_name_snap", length = 255)
    private String companyNameSnap;

    @Column(name = "document_url", length = 500)
    private String documentUrl;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "cva_reviewer_id")
    private Long cvaReviewerId;

    @Column(name = "cva_note", columnDefinition = "text")
    private String cvaNote;

    @Column(name = "admin_reviewer_id")
    private Long adminReviewerId;

    @Column(name = "admin_note", columnDefinition = "text")
    private String adminNote;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
