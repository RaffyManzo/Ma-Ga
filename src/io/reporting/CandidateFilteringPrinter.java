package io.reporting;

import window.prefilter.CandidateFilteringResult;
import window.prefilter.CandidateRejectionReason;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;

/**
 * Printer sintetico per verificare l'effetto del CandidatePrefilter.
 */
public final class CandidateFilteringPrinter {

    private final PrintStream out;

    public CandidateFilteringPrinter() {
        this(System.out);
    }

    public CandidateFilteringPrinter(PrintStream out) {
        if (out == null) {
            throw new IllegalArgumentException("out must not be null.");
        }

        this.out = out;
    }

    public void print(List<CandidateFilteringResult> results) {
        out.println("------------------------------------------------------------");
        out.println("CANDIDATE PREFILTER SUMMARY");
        out.println("------------------------------------------------------------");

        if (results == null || results.isEmpty()) {
            out.println("No filtering result available.");
            out.println();
            return;
        }

        out.println("idx | snapshot | original | filtered | removed | reasons");

        for (int i = 0; i < results.size(); i++) {
            CandidateFilteringResult result = results.get(i);

            out.println(i
                    + " | " + result.getFilteredSnapshot().getSnapshotId()
                    + " | " + result.getStats().getOriginalCandidateCount()
                    + " | " + result.getStats().getFilteredCandidateCount()
                    + " | " + result.getStats().getRemovedCandidateCount()
                    + " | " + formatReasons(
                    result.getStats().getCountByReason()
            ));
        }

        out.println();
    }

    private String formatReasons(
            Map<CandidateRejectionReason, Integer> reasonMap
    ) {
        StringBuilder builder = new StringBuilder();

        for (CandidateRejectionReason reason
                : CandidateRejectionReason.values()) {
            int count = reasonMap.getOrDefault(reason, 0);

            if (count <= 0) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(", ");
            }

            builder.append(reason).append("=").append(count);
        }

        return builder.toString();
    }
}
