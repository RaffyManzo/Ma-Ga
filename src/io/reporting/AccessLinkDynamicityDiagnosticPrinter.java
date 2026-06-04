package io.reporting;

import config.mobility.MobilityConfig;
import model.mobility.AccessLinkMetrics;
import model.mobility.AccessLinkMetricsEstimator;
import model.snapshot.SystemSnapshot;
import model.snapshot.VehicleSnapshot;
import window.dynamicity.math.DynamicityMath;
import window.source.SystemStateObservation;
import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Report diagnostico della componente Dl(k) gateway-aware.
 *
 * <p>Mostra la qualità q_v(k) degli access link, le variazioni tra finestre
 * consecutive e gli eventuali handover osservati. Non modifica le decisioni
 * del MA-GA.</p>
 */
public final class AccessLinkDynamicityDiagnosticPrinter {
    private final PrintStream out;
    private final int topK;
    private final AccessLinkMetricsEstimator estimator;

    public AccessLinkDynamicityDiagnosticPrinter(
            MobilityConfig mobilityConfig,
            PrintStream out,
            int topK
    ) {
        this.out = Objects.requireNonNull(out, "out must not be null.");
        if (topK < 0) {
            throw new IllegalArgumentException("topK must be >= 0.");
        }
        this.topK = topK;
        this.estimator = new AccessLinkMetricsEstimator(
                Objects.requireNonNull(
                        mobilityConfig,
                        "mobilityConfig must not be null."
                )
        );
    }

    public void print(TemporalWindowResult result) {
        Objects.requireNonNull(result, "result must not be null.");
        printSummary(result);
        printTopChanges(result);
        printInterpretation();
    }

    private void printSummary(TemporalWindowResult result) {
        title("ACCESS LINK DYNAMICITY DIAGNOSTIC");
        out.println("idx | snapshot | commonVehicles | avgPrevQ | avgCurrQ | Dl | degradedLinks | recoveredLinks | unavailableLinks | coverageLosses | coverageGains | handovers");

        SystemSnapshot previous = null;
        for (TemporalStepResult step : result.getSteps()) {
            SystemSnapshot current = observed(step);
            if (previous == null) {
                out.println(step.getWindowIndex() + " | " + current.getSnapshotId()
                        + " | 0 | - | - | 0,000000 | 0 | 0 | 0 | 0");
                previous = current;
                continue;
            }

            List<LinkQualityChange> changes = changes(previous, current);
            double avgPrevQ = average(changes, true);
            double avgCurrQ = average(changes, false);
            double dl = averageDelta(changes);
            int degraded = 0;
            int recovered = 0;
            int unavailable = 0;
            int coverageLosses = 0;
            int coverageGains = 0;
            int handovers = 0;

            for (LinkQualityChange change : changes) {
                if (change.deltaQuality() < 0.0) { degraded++; }
                if (change.deltaQuality() > 0.0) { recovered++; }
                if (!change.currentAvailable()) { unavailable++; }
                if (change.transition() == GatewayTransition.COVERAGE_LOSS) { coverageLosses++; }
                if (change.transition() == GatewayTransition.COVERAGE_GAIN) { coverageGains++; }
                if (change.transition() == GatewayTransition.HANDOVER) { handovers++; }
            }

            printf("%d | %s | %d | %s | %s | %.6f | %d | %d | %d | %d | %d | %d%n",
                    step.getWindowIndex(),
                    current.getSnapshotId(),
                    changes.size(),
                    numberOrDash(avgPrevQ),
                    numberOrDash(avgCurrQ),
                    dl,
                    degraded,
                    recovered,
                    unavailable,
                    coverageLosses,
                    coverageGains,
                    handovers
            );
            previous = current;
        }
        out.println();
    }

    private void printTopChanges(TemporalWindowResult result) {
        title("TOP ACCESS LINK QUALITY CHANGES");
        out.println("idx | vehicle | previousGateway | currentGateway | prevAvailable | currAvailable | prevDistance | currDistance | prevPhiLink | currPhiLink | prevQ | currQ | deltaQ | transition");

        List<IndexedChange> all = new ArrayList<>();
        SystemSnapshot previous = null;
        for (TemporalStepResult step : result.getSteps()) {
            SystemSnapshot current = observed(step);
            if (previous != null) {
                for (LinkQualityChange change : changes(previous, current)) {
                    if (topK == 0
                            || change.absoluteDeltaQuality() > 0.0
                            || change.transition() != GatewayTransition.UNCHANGED) {
                        all.add(new IndexedChange(step.getWindowIndex(), change));
                    }
                }
            }
            previous = current;
        }

        all.sort(Comparator
                .comparingDouble((IndexedChange value) -> value.change().absoluteDeltaQuality())
                .reversed()
                .thenComparingInt(IndexedChange::windowIndex)
                .thenComparing(value -> value.change().vehicleId()));

        int limit = topK == 0 ? all.size() : Math.min(topK, all.size());
        for (int i = 0; i < limit; i++) {
            IndexedChange indexed = all.get(i);
            LinkQualityChange change = indexed.change();
            printf("%d | %s | %s | %s | %s | %s | %s | %s | %s | %s | %.6f | %.6f | %.6f | %s%n",
                    indexed.windowIndex(),
                    change.vehicleId(),
                    textOrDash(change.previousGatewayId()),
                    textOrDash(change.currentGatewayId()),
                    change.previousAvailable(),
                    change.currentAvailable(),
                    numberOrDash(change.previousDistanceMeters()),
                    numberOrDash(change.currentDistanceMeters()),
                    numberOrDash(change.previousPhiLink()),
                    numberOrDash(change.currentPhiLink()),
                    change.previousQuality(),
                    change.currentQuality(),
                    change.deltaQuality(),
                    change.transition()
            );
        }
        out.println();
    }

    private void printInterpretation() {
        title("ACCESS LINK DYNAMICITY INTERPRETATION");
        out.println("- Dv(k) now measures only vehicle entry/exit churn.");
        out.println("- Dl(k) is the mean absolute variation of q_v(k) over vehicles present in both consecutive windows.");
        out.println("- q_v(k) = 1 - phi_link(v, activeGateway); unavailable or missing access links have q_v(k) = 0.");
        out.println("- Missing gateways do not create synthetic distance or phi_link metrics; unavailable metrics are rendered as '-'.");
        out.println("- Coverage loss and coverage gain are reported separately from gateway-to-gateway handover.");
        out.println("- Computational candidate additions do not alter Dl(k) when active access links stay unchanged.");
        out.println("- A gateway transition is reported separately. It affects Dl(k) only through the resulting quality variation.");
        out.println();
    }

    private List<LinkQualityChange> changes(
            SystemSnapshot previous,
            SystemSnapshot current
    ) {
        Set<String> common = vehicleIds(previous);
        common.retainAll(vehicleIds(current));
        List<LinkQualityChange> result = new ArrayList<>();

        for (String vehicleId : common) {
            LinkQualityState before = state(previous, vehicleId);
            LinkQualityState after = state(current, vehicleId);
            result.add(new LinkQualityChange(
                    vehicleId,
                    before.gatewayId(),
                    after.gatewayId(),
                    before.available(),
                    after.available(),
                    before.distanceMeters(),
                    after.distanceMeters(),
                    before.phiLink(),
                    after.phiLink(),
                    before.quality(),
                    after.quality()
            ));
        }
        return result;
    }

    private LinkQualityState state(SystemSnapshot snapshot, String vehicleId) {
        Optional<AccessLinkMetrics> maybeMetrics =
                estimator.estimateActiveLinkIfPresent(snapshot, vehicleId);
        if (maybeMetrics.isEmpty()) {
            return LinkQualityState.noActiveLink();
        }

        AccessLinkMetrics metrics = maybeMetrics.get();
        return new LinkQualityState(
                metrics.getGatewayId(),
                metrics.isAvailable(),
                metrics.getDistanceMeters(),
                metrics.getLinkInstability(),
                quality(metrics),
                true
        );
    }

    private double quality(AccessLinkMetrics metrics) {
        if (!metrics.isAvailable()) {
            return 0.0;
        }
        return DynamicityMath.clamp01(1.0 - metrics.getLinkInstability());
    }

    private Set<String> vehicleIds(SystemSnapshot snapshot) {
        Set<String> result = new HashSet<>();
        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            result.add(vehicle.getVehicleId());
        }
        return result;
    }

    private double average(List<LinkQualityChange> changes, boolean previous) {
        if (changes.isEmpty()) { return Double.NaN; }
        double sum = 0.0;
        for (LinkQualityChange change : changes) {
            sum += previous ? change.previousQuality() : change.currentQuality();
        }
        return sum / changes.size();
    }

    private double averageDelta(List<LinkQualityChange> changes) {
        if (changes.isEmpty()) { return 0.0; }
        double sum = 0.0;
        for (LinkQualityChange change : changes) {
            sum += change.absoluteDeltaQuality();
        }
        return DynamicityMath.clamp01(sum / changes.size());
    }

    private SystemSnapshot observed(TemporalStepResult step) {
        return step.getSystemStateObservation()
                .map(SystemStateObservation::getObservedSnapshot)
                .orElse(step.getSnapshot());
    }

    private String numberOrDash(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ITALY, "%.6f", value)
                : "-";
    }

    private String textOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void printf(String format, Object... values) {
        out.printf(Locale.ITALY, format, values);
    }

    private void title(String value) {
        out.println("------------------------------------------------------------");
        out.println(value);
        out.println("------------------------------------------------------------");
    }

    private record IndexedChange(int windowIndex, LinkQualityChange change) { }

    private record LinkQualityState(
            String gatewayId,
            boolean available,
            double distanceMeters,
            double phiLink,
            double quality,
            boolean hasActiveAccessLink
    ) {
        private static LinkQualityState noActiveLink() {
            return new LinkQualityState(
                    null,
                    false,
                    Double.NaN,
                    Double.NaN,
                    0.0,
                    false
            );
        }
    }

    private enum GatewayTransition {
        UNCHANGED,
        COVERAGE_GAIN,
        COVERAGE_LOSS,
        HANDOVER
    }

    private record LinkQualityChange(
            String vehicleId,
            String previousGatewayId,
            String currentGatewayId,
            boolean previousAvailable,
            boolean currentAvailable,
            double previousDistanceMeters,
            double currentDistanceMeters,
            double previousPhiLink,
            double currentPhiLink,
            double previousQuality,
            double currentQuality
    ) {
        private double deltaQuality() {
            return currentQuality - previousQuality;
        }

        private double absoluteDeltaQuality() {
            return Math.abs(deltaQuality());
        }

        private GatewayTransition transition() {
            if (previousGatewayId == null && currentGatewayId == null) {
                return GatewayTransition.UNCHANGED;
            }
            if (previousGatewayId == null) {
                return GatewayTransition.COVERAGE_GAIN;
            }
            if (currentGatewayId == null) {
                return GatewayTransition.COVERAGE_LOSS;
            }
            if (Objects.equals(previousGatewayId, currentGatewayId)) {
                return GatewayTransition.UNCHANGED;
            }
            return GatewayTransition.HANDOVER;
        }
    }
}
