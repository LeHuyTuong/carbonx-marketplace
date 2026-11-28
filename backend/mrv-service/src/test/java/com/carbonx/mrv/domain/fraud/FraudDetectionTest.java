package com.carbonx.mrv.domain.fraud;

import com.carbonx.mrv.domain.report.EmissionDataset;
import com.carbonx.mrv.domain.report.EmissionRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Deterministic gate for the eight data-quality rules that also feed the AI fallback score. */
class FraudDetectionTest {

    private final FraudDetector detector = new FraudDetector();

    private static EmissionDataset dataset(String... rows) {
        return new EmissionDataset(
                List.of(EmissionDataset.COL_PERIOD, EmissionDataset.COL_ENERGY, EmissionDataset.COL_PLATE),
                java.util.Arrays.stream(rows).map(FraudDetectionTest::row).toList());
    }

    private static EmissionRow row(String csv) {
        String[] cells = csv.split(",");
        return new EmissionRow(cells[0], cells[1], cells[2]);
    }

    @Test
    @DisplayName("A clean dataset runs DQ1..DQ8 in order and scores 100/100")
    void cleanDatasetScoresFullMarks() {
        EmissionDataset dataset = dataset(
                "2026-09,100.0,51A12345",
                "2026-09,130.5,51A23456",
                "2026-09,95.2,51A34567",
                "2026-09,210.7,51A45678",
                "2026-09,180.3,51A56789");

        FraudAssessment assessment = detector.assess(dataset);

        assertThat(assessment.findings()).hasSize(8);
        assertThat(assessment.findings()).extracting(FraudFinding::ruleCode).containsExactly(
                "DQ1_SCHEMA", "DQ2_PERIOD", "DQ3_ENERGY", "DQ4_DUP_PLATE",
                "DQ5_DUP_ROW", "DQ6_OUTLIER_IQR", "DQ7_UNIFORMITY_CV", "DQ8_REPEATED_ROUNDED");
        assertThat(assessment.isClean()).isTrue();
        assertThat(assessment.weightedScore()).isEqualByComparingTo("100.00");
        assertThat(dataset.totalEnergyKwh()).isEqualByComparingTo("716.7000");
        assertThat(dataset.vehicleCount()).isEqualTo(5);
        assertThat(dataset.resolvedPeriod()).isEqualTo("2026-09");
    }

    @Test
    @DisplayName("Duplicated plates, repeated rows and mixed periods are penalised")
    void fabricatedDatasetLosesPoints() {
        EmissionDataset dataset = dataset(
                "2026-09,120.0,51A12345",
                "2026-09,120.0,51A12345",
                "2026-08,120.0,51A12345",
                "2026-09,120.0,51A12345");

        FraudAssessment assessment = detector.assess(dataset);

        assertThat(assessment.isClean()).isFalse();
        // Three repeated plates and two byte-identical rows: both rules drop to their middle tier.
        assertThat(find(assessment, FraudRuleCode.DQ4_DUP_PLATE).score()).isEqualTo(3);
        assertThat(find(assessment, FraudRuleCode.DQ2_PERIOD).passed()).isFalse();
        assertThat(find(assessment, FraudRuleCode.DQ5_DUP_ROW).score()).isEqualTo(3);
        assertThat(assessment.weightedScore()).isLessThan(new BigDecimal("80.00"));
    }

    @Test
    @DisplayName("A file without the plate column fails DQ1 and reports zero vehicles")
    void missingColumnsFailSchemaRule() {
        EmissionDataset dataset = new EmissionDataset(
                List.of(EmissionDataset.COL_PERIOD, EmissionDataset.COL_ENERGY),
                List.of(new EmissionRow("2026-09", "42.0", null)));

        FraudAssessment assessment = detector.assess(dataset);

        assertThat(find(assessment, FraudRuleCode.DQ1_SCHEMA).passed()).isFalse();
        assertThat(find(assessment, FraudRuleCode.DQ1_SCHEMA).evidence()).contains("license_plate");
        assertThat(dataset.vehicleCount()).isZero();
    }

    @Test
    @DisplayName("Non-numeric and non-positive energy values are scored by DQ3, not silently dropped")
    void invalidEnergyValuesArePenalised() {
        EmissionDataset dataset = dataset(
                "2026-09,not-a-number,51A12345",
                "2026-09,-5,51A23456",
                "2026-09,0,51A34567",
                "2026-09,,51A45678");

        FraudAssessment assessment = detector.assess(dataset);

        assertThat(find(assessment, FraudRuleCode.DQ3_ENERGY).score()).isZero();
        assertThat(dataset.totalEnergyKwh()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Uniform machine-generated values trip DQ7")
    void uniformValuesAreSuspicious() {
        EmissionDataset dataset = dataset(
                "2026-09,100.00,51A12345",
                "2026-09,100.01,51A23456",
                "2026-09,100.02,51A34567");

        FraudAssessment assessment = detector.assess(dataset);

        assertThat(find(assessment, FraudRuleCode.DQ7_UNIFORMITY_CV).passed()).isFalse();
        assertThat(find(assessment, FraudRuleCode.DQ7_UNIFORMITY_CV).score()).isEqualTo(2);
    }

    @Test
    @DisplayName("A single malformed row is tolerated by DQ2 within the 5% tolerance")
    void smallPeriodNoiseIsTolerated() {
        EmissionDataset dataset = dataset(
                "2026-09,100.0,51A12345",
                "2026-09,120.0,51A23456",
                "2026-09,140.0,51A34567",
                "2026-9,160.0,51A45678",
                "2026-09,180.0,51A56789");

        FraudAssessment assessment = detector.assess(dataset);

        assertThat(find(assessment, FraudRuleCode.DQ2_PERIOD).passed()).isFalse();
        assertThat(find(assessment, FraudRuleCode.DQ2_PERIOD).score()).isEqualTo(3);
    }

    private static FraudFinding find(FraudAssessment assessment, FraudRuleCode code) {
        return assessment.findings().stream()
                .filter(finding -> finding.ruleCode().equals(code.name()))
                .findFirst()
                .orElseThrow();
    }
}
