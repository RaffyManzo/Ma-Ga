# Literature Scenario Smoke Validation

- status: LITERATURE_SMOKE_TEST_PASSED
- run: log-20260626-221649-MaGaLiteratureBasedUrbanStudy
- scenario: MaGaLiteratureBasedUrbanStudy
- simulationCompleted: True
- taskCountersSource: LIVE_MAGA_RUNTIME_COORDINATOR_TICK
- taskCompletionModel: NOT_IMPLEMENTED
- gaParameterScalingMode: STATIC
- gaRuntimeMeanSeconds: 0.593730419
- gaRuntimeMedianSeconds: 0.0025779
- gaRuntimeP95Seconds: 4.1520194
- gaRuntimeMaxSeconds: 14.18626
- staleRatioPercent: 16.216216
- staleSequenceCount: 2
- longestConsecutiveStaleSequence: 5
- maximumAbsoluteSnapshotLagSeconds: 0
- nonZeroLagWindowCount: 0
- lastAppliedStrategySimulationTimeSeconds: 250.7
- secondsWithoutAppliedStrategyAtEnd: 49.3
- tasksGeneratedCumulative: 5667
- tasksActivatedCumulative: 5667
- tasksRemovedAtDeadlineCumulative: 5650
- tasksPendingAtEnd: 17
- tasksPendingPeak: 43
- snapshots: 2981 / 3000
- GA jobs completed/submitted: 37 / 38
- strategyApplications: 31
- assignments LOCAL/VEHICLE/EDGE/CLOUD: 17 / 2 / 0 / 0
- violations parallel/futureSnapshot/futurePool/invalidPool/deltaTMaxMismatch: 0 / 0 / 0 / 0 / 0

## Task lifecycle note

Tasks removed at deadline are not reported as completed. The live prototype does not simulate task execution completion.

## Artifacts

- gaJobEventsJsonl: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-221649-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_ga_job_events.jsonl
- temporalStepRecordsJsonl: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-221649-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_temporal_step_records.jsonl
- detailedReportTxt: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-221649-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.txt
- detailedReportMarkdown: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-221649-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.md
- detailedReportJson: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-221649-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.json

## Errors

- none
