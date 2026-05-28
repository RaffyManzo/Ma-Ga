package io.reporting.diagnostics.deadline;

import ga.fitness.breakdown.GeneEvaluationBreakdown;
import model.node.NodeType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Analizzatore diagnostico delle deadline.
 *
 * La versione raffinata distingue meglio:
 *
 * - task completamente locali;
 * - task parzialmente offloadati ma dominati dal ramo locale;
 * - task parzialmente offloadati ma dominati dal ramo remoto;
 * - veri casi misti locale/remoto;
 * - problemi di copertura.
 *
 * La classe non modifica fitness, repair o cromosomi.
 */
public final class DeadlineViolationAnalyzer {

    private static final double EPSILON = 1.0E-9;

    /*
     * Se un ramo pesa almeno il 10% più dell'altro, viene considerato
     * dominante. Se la differenza è minore, il caso resta misto.
     */
    private static final double BRANCH_DOMINANCE_MARGIN = 0.10;

    /*
     * Se local e remote sono molto vicini, il task viene trattato come
     * mixed critical path.
     */
    private static final double BRANCH_BALANCE_THRESHOLD = 0.85;

    /*
     * Soglia per dire che una componente remota domina la pipeline remota.
     */
    private static final double REMOTE_COMPONENT_DOMINANCE_RATIO = 0.45;

    /**
     * Diagnostica tutti i task della finestra.
     */
    public DeadlineViolationWindowSummary summarize(
            List<GeneEvaluationBreakdown> genes
    ) {
        List<DeadlineViolationDiagnosis> diagnoses = new ArrayList<>();
        Map<DeadlineViolationCause, Integer> countByCause =
                new EnumMap<>(DeadlineViolationCause.class);

        int violated = 0;

        for (GeneEvaluationBreakdown gene : genes) {
            DeadlineViolationDiagnosis diagnosis = diagnose(gene);
            diagnoses.add(diagnosis);

            countByCause.merge(
                    diagnosis.getPrimaryCause(),
                    1,
                    Integer::sum
            );

            if (diagnosis.isDeadlineViolated()) {
                violated++;
            }
        }

        List<DeadlineViolationDiagnosis> violationsBySeverity =
                diagnoses.stream()
                        .filter(DeadlineViolationDiagnosis::isDeadlineViolated)
                        .sorted(
                                Comparator
                                        .comparingDouble(
                                                DeadlineViolationDiagnosis::getViolationRatio
                                        )
                                        .reversed()
                        )
                        .toList();

        int total = genes.size();
        int respected = total - violated;
        double violationRate = total == 0
                ? 0.0
                : (double) violated / total;

        return new DeadlineViolationWindowSummary(
                total,
                respected,
                violated,
                violationRate,
                countByCause,
                diagnoses,
                violationsBySeverity
        );
    }

    /**
     * Diagnostica un singolo task.
     */
    public DeadlineViolationDiagnosis diagnose(
            GeneEvaluationBreakdown gene
    ) {
        double completion = safe(gene.getCompletionTimeSeconds());
        double deadline = safe(gene.getDeadlineSeconds());
        double violationSeconds = Math.max(0.0, completion - deadline);
        double violationRatio = deadline <= EPSILON
                ? 0.0
                : violationSeconds / deadline;

        double local = safe(gene.getLocalExecutionTimeSeconds());
        double remote = safe(gene.getRemotePartTimeSeconds());

        double localRatio = completion <= EPSILON
                ? 0.0
                : local / completion;

        double remoteRatio = completion <= EPSILON
                ? 0.0
                : remote / completion;

        double branchBalance = Math.min(local, remote)
                / Math.max(Math.max(local, remote), EPSILON);

        DeadlineViolationCause primaryCause;
        DeadlineViolationCause secondaryCause;

        if (violationSeconds <= EPSILON) {
            primaryCause = DeadlineViolationCause.DEADLINE_RESPECTED;
            secondaryCause = DeadlineViolationCause.DEADLINE_RESPECTED;
        } else {
            primaryCause = classifyPrimaryCause(
                    gene,
                    local,
                    remote,
                    branchBalance
            );

            secondaryCause = classifySecondaryCause(
                    gene,
                    primaryCause
            );
        }

        Component dominant = dominantComponent(gene);

        return new DeadlineViolationDiagnosis(
                gene.getTaskId(),
                gene.getSourceVehicleId(),
                gene.getSelectedCandidateId(),
                gene.getExecutionNodeId(),
                gene.getNodeType(),
                gene.getDecisionType(),
                safe(gene.getOffloadingRatio()),
                safe(gene.getAllocatedCpu()),
                safe(gene.getAllocatedBandwidth()),
                completion,
                deadline,
                violationSeconds,
                violationRatio,
                local,
                safe(gene.getUploadTimeSeconds()),
                safe(gene.getRemoteExecutionTimeSeconds()),
                safe(gene.getDownloadTimeSeconds()),
                safe(gene.getBaseLatencySeconds()),
                remote,
                safe(gene.getCommunicationLatencySeconds()),
                localRatio,
                remoteRatio,
                branchBalance,
                safe(gene.getCoverageTimeSeconds()),
                gene.isCoverageSufficient(),
                safe(gene.getMobilityPenalty()),
                safe(gene.getConstraintPenalty()),
                primaryCause,
                secondaryCause,
                dominant.name,
                dominant.seconds,
                buildNote(
                        gene,
                        primaryCause,
                        secondaryCause,
                        localRatio,
                        remoteRatio,
                        branchBalance,
                        dominant
                )
        );
    }

    private DeadlineViolationCause classifyPrimaryCause(
            GeneEvaluationBreakdown gene,
            double local,
            double remote,
            double branchBalance
    ) {
        if (gene.getNodeType() != NodeType.LOCAL
                && !gene.isCoverageSufficient()) {
            return DeadlineViolationCause.COVERAGE_INSUFFICIENT;
        }

        if (gene.getNodeType() == NodeType.LOCAL) {
            return DeadlineViolationCause.LOCAL_EXECUTION_BOTTLENECK;
        }

        boolean localDominates =
                local > remote * (1.0 + BRANCH_DOMINANCE_MARGIN);

        boolean remoteDominates =
                remote > local * (1.0 + BRANCH_DOMINANCE_MARGIN);

        if (localDominates) {
            return DeadlineViolationCause.LOCAL_BRANCH_DOMINATES;
        }

        if (remoteDominates) {
            DeadlineViolationCause remoteCause =
                    classifyRemotePipeline(gene);

            if (remoteCause == DeadlineViolationCause.UNKNOWN
                    || remoteCause == DeadlineViolationCause.MIXED_REMOTE_PIPELINE_BOTTLENECK) {
                return DeadlineViolationCause.REMOTE_BRANCH_DOMINATES;
            }

            return remoteCause;
        }

        if (branchBalance >= BRANCH_BALANCE_THRESHOLD) {
            return DeadlineViolationCause.MIXED_LOCAL_REMOTE_BOTTLENECK;
        }

        return classifyRemotePipeline(gene);
    }

    private DeadlineViolationCause classifySecondaryCause(
            GeneEvaluationBreakdown gene,
            DeadlineViolationCause primaryCause
    ) {
        if (primaryCause == DeadlineViolationCause.COVERAGE_INSUFFICIENT) {
            return classifyRemotePipeline(gene);
        }

        if (primaryCause == DeadlineViolationCause.LOCAL_BRANCH_DOMINATES) {
            return classifyRemotePipeline(gene);
        }

        if (primaryCause == DeadlineViolationCause.REMOTE_BRANCH_DOMINATES) {
            return DeadlineViolationCause.LOCAL_BRANCH_DOMINATES;
        }

        if (gene.getNodeType() != NodeType.LOCAL
                && !gene.isCoverageSufficient()) {
            return DeadlineViolationCause.COVERAGE_INSUFFICIENT;
        }

        return DeadlineViolationCause.UNKNOWN;
    }

    private DeadlineViolationCause classifyRemotePipeline(
            GeneEvaluationBreakdown gene
    ) {
        double upload = safe(gene.getUploadTimeSeconds());
        double remoteExecution = safe(gene.getRemoteExecutionTimeSeconds());
        double download = safe(gene.getDownloadTimeSeconds());
        double baseLatency = safe(gene.getBaseLatencySeconds());

        double remotePart = Math.max(
                safe(gene.getRemotePartTimeSeconds()),
                upload + remoteExecution + download + baseLatency
        );

        if (remotePart <= EPSILON) {
            return DeadlineViolationCause.UNKNOWN;
        }

        Component dominant = maxComponent(
                new Component("upload", upload),
                new Component("remoteExecution", remoteExecution),
                new Component("download", download),
                new Component("baseLatency", baseLatency)
        );

        double dominanceRatio = dominant.seconds / remotePart;

        if (dominanceRatio < REMOTE_COMPONENT_DOMINANCE_RATIO) {
            return DeadlineViolationCause.MIXED_REMOTE_PIPELINE_BOTTLENECK;
        }

        return switch (dominant.name) {
            case "upload" -> DeadlineViolationCause.UPLOAD_BOTTLENECK;
            case "remoteExecution" -> DeadlineViolationCause.REMOTE_EXECUTION_BOTTLENECK;
            case "download" -> DeadlineViolationCause.DOWNLOAD_BOTTLENECK;
            case "baseLatency" -> DeadlineViolationCause.BASE_LATENCY_BOTTLENECK;
            default -> DeadlineViolationCause.UNKNOWN;
        };
    }

    private Component dominantComponent(
            GeneEvaluationBreakdown gene
    ) {
        return maxComponent(
                new Component("localExecution", safe(gene.getLocalExecutionTimeSeconds())),
                new Component("upload", safe(gene.getUploadTimeSeconds())),
                new Component("remoteExecution", safe(gene.getRemoteExecutionTimeSeconds())),
                new Component("download", safe(gene.getDownloadTimeSeconds())),
                new Component("baseLatency", safe(gene.getBaseLatencySeconds()))
        );
    }

    private Component maxComponent(Component... components) {
        Component best = components[0];

        for (Component component : components) {
            if (component.seconds > best.seconds) {
                best = component;
            }
        }

        return best;
    }

    private String buildNote(
            GeneEvaluationBreakdown gene,
            DeadlineViolationCause primaryCause,
            DeadlineViolationCause secondaryCause,
            double localRatio,
            double remoteRatio,
            double branchBalance,
            Component dominant
    ) {
        if (primaryCause == DeadlineViolationCause.DEADLINE_RESPECTED) {
            return "Deadline respected.";
        }

        if (primaryCause == DeadlineViolationCause.LOCAL_EXECUTION_BOTTLENECK) {
            return "Pure local execution is slower than the task deadline.";
        }

        if (primaryCause == DeadlineViolationCause.LOCAL_BRANCH_DOMINATES) {
            return "Partial offloading is selected, but the local branch dominates the critical path.";
        }

        if (primaryCause == DeadlineViolationCause.REMOTE_BRANCH_DOMINATES) {
            return "Remote branch dominates the critical path, but no single remote component dominates enough.";
        }

        if (primaryCause == DeadlineViolationCause.MIXED_LOCAL_REMOTE_BOTTLENECK) {
            return "Local and remote branches are both close to the critical path; branchBalance="
                    + format(branchBalance)
                    + ".";
        }

        if (primaryCause == DeadlineViolationCause.COVERAGE_INSUFFICIENT) {
            return "Remote candidate has insufficient coverage; prefilter or mobility-aware repair should remove it.";
        }

        return "Dominant component="
                + dominant.name
                + ", localRatio="
                + format(localRatio)
                + ", remoteRatio="
                + format(remoteRatio)
                + ", secondary="
                + secondaryCause
                + ".";
    }

    private double safe(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }

        return Math.max(0.0, value);
    }

    private String format(double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }

        return String.format("%.6f", value);
    }

    private static final class Component {

        private final String name;
        private final double seconds;

        private Component(String name, double seconds) {
            this.name = name;
            this.seconds = seconds;
        }
    }
}
