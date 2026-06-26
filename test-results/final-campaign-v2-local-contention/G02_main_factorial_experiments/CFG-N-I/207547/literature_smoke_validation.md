# Literature Scenario Smoke Validation

- status: LITERATURE_SMOKE_TEST_PASSED
- run: log-20260626-231457-MaGaLiteratureBasedUrbanStudy
- scenario: MaGaLiteratureBasedUrbanStudy
- simulationCompleted: True
- taskCountersSource: LIVE_MAGA_RUNTIME_COORDINATOR_TICK
- taskCompletionModel: NOT_IMPLEMENTED
- gaParameterScalingMode: STATIC
- gaRuntimeMeanSeconds: 0.645070503
- gaRuntimeMedianSeconds: 0.0011991
- gaRuntimeP95Seconds: 2.958133
- gaRuntimeMaxSeconds: 17.2247324
- staleRatioPercent: 9.090909
- staleSequenceCount: 1
- longestConsecutiveStaleSequence: 3
- maximumAbsoluteSnapshotLagSeconds: 0
- nonZeroLagWindowCount: 0
- lastAppliedStrategySimulationTimeSeconds: 12.8
- secondsWithoutAppliedStrategyAtEnd: 287.2
- tasksGeneratedCumulative: 9005
- tasksActivatedCumulative: 9005
- tasksRemovedAtDeadlineCumulative: 8950
- tasksPendingAtEnd: 55
- tasksPendingPeak: 86
- snapshots: 2981 / 3000
- GA jobs completed/submitted: 33 / 33
- strategyApplications: 30
- assignments LOCAL/VEHICLE/EDGE/CLOUD: 28 / 1 / 0 / 0
- violations parallel/futureSnapshot/futurePool/invalidPool/deltaTMaxMismatch: 0 / 0 / 0 / 0 / 0

## Task lifecycle note

Tasks removed at deadline are not reported as completed. The live prototype does not simulate task execution completion.

## Artifacts

- gaJobEventsJsonl: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-231457-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_ga_job_events.jsonl
- temporalStepRecordsJsonl: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-231457-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_temporal_step_records.jsonl
- detailedReportTxt: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-231457-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.txt
- detailedReportMarkdown: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-231457-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.md
- detailedReportJson: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-231457-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.json

## Errors

- none
