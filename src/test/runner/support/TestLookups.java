package test.runner.support;

import ga.fitness.breakdown.EvaluationBreakdown;
import ga.fitness.breakdown.ExecutionNodeResourceUsageBreakdown;
import ga.fitness.breakdown.LinkBandwidthUsageBreakdown;
import model.genetic.Chromosome;
import model.genetic.Gene;
import model.node.NodeCandidate;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;

/**
 * Lookup e controlli strutturali condivisi dai test.
 */
public final class TestLookups {

    private TestLookups() {
    }

    public static void assertChromosomeUsesOnlyValidCandidates(
            Chromosome chromosome,
            SystemSnapshot snapshot
    ) {
        for (Gene gene : chromosome.getGenes()) {
            TaskInstance task = findTask(snapshot, gene.getTaskId());
            NodeCandidate candidate = findCandidate(
                    snapshot,
                    gene.getSelectedCandidateId()
            );

            TestAssertions.assertTrue(
                    task != null,
                    "Task should exist for gene " + gene.getTaskId()
            );

            TestAssertions.assertTrue(
                    candidate != null,
                    "Candidate should exist for gene "
                            + gene.getSelectedCandidateId()
            );

            TestAssertions.assertEquals(
                    task.getSourceVehicleId(),
                    candidate.getSourceVehicleId(),
                    "Gene candidate must match task sourceVehicleId."
            );
        }
    }

    public static ExecutionNodeResourceUsageBreakdown findExecutionNodeUsage(
            EvaluationBreakdown breakdown,
            String executionNodeId
    ) {
        for (ExecutionNodeResourceUsageBreakdown usage
                : breakdown.getExecutionNodeResourceUsageBreakdowns()) {
            if (usage.getExecutionNodeId().equals(executionNodeId)) {
                return usage;
            }
        }

        throw new AssertionError(
                "Execution node usage not found: " + executionNodeId
        );
    }

    public static LinkBandwidthUsageBreakdown findLinkUsage(
            EvaluationBreakdown breakdown,
            String candidateId
    ) {
        for (LinkBandwidthUsageBreakdown usage
                : breakdown.getLinkBandwidthUsageBreakdowns()) {
            if (usage.getCandidateId().equals(candidateId)) {
                return usage;
            }
        }

        throw new AssertionError("Link usage not found: " + candidateId);
    }

    private static TaskInstance findTask(
            SystemSnapshot snapshot,
            String taskId
    ) {
        for (TaskInstance task : snapshot.getTasks()) {
            if (task.getTaskId().equals(taskId)) {
                return task;
            }
        }

        return null;
    }

    private static NodeCandidate findCandidate(
            SystemSnapshot snapshot,
            String candidateId
    ) {
        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            if (candidate.getCandidateId().equals(candidateId)) {
                return candidate;
            }
        }

        return null;
    }
}
