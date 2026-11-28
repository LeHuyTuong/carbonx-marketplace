package com.carbonx.mrv.infrastructure.persistence.entity;

import com.carbonx.mrv.domain.fraud.FraudFinding;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** JPA row of {@code fraud_analyses}: the immutable DQ1..DQ8 audit trail of one report. */
@Entity
@Table(name = "fraud_analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FraudAnalysisJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false, length = 255)
    private String ruleName;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "max_score", nullable = false)
    private int maxScore;

    @Column(name = "passed", nullable = false)
    private boolean passed;

    @Column(name = "evidence", columnDefinition = "text")
    private String evidence;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public static FraudAnalysisJpaEntity of(Long reportId, FraudFinding finding) {
        FraudAnalysisJpaEntity entity = new FraudAnalysisJpaEntity();
        entity.reportId = reportId;
        entity.ruleCode = finding.ruleCode();
        entity.ruleName = finding.ruleName();
        entity.score = finding.score();
        entity.maxScore = finding.maxScore();
        entity.passed = finding.passed();
        entity.evidence = finding.evidence();
        return entity;
    }

    public FraudFinding toDomain() {
        return new FraudFinding(ruleCode, ruleName, score, maxScore, passed, evidence);
    }
}
