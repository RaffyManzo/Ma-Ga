# Literature Scenario Smoke Validation

- status: LITERATURE_SMOKE_TEST_PASSED
- run: log-20260626-231224-MaGaLiteratureBasedUrbanStudy
- scenario: MaGaLiteratureBasedUrbanStudy
- simulationCompleted: True
- taskCountersSource: LIVE_MAGA_RUNTIME_COORDINATOR_TICK
- taskCompletionModel: NOT_IMPLEMENTED
- gaParameterScalingMode: STATIC
- gaRuntimeMeanSeconds: 0.341507632
- gaRuntimeMedianSeconds: 0.0011254
- gaRuntimeP95Seconds: 1.8722296
- gaRuntimeMaxSeconds: 7.9842777
- staleRatioPercent: 9.677419
- staleSequenceCount: 1
- longestConsecutiveStaleSequence: 3
- maximumAbsoluteSnapshotLagSeconds: 0
- nonZeroLagWindowCount: 0
- lastAppliedStrategySimulationTimeSeconds: 14.7
- secondsWithoutAppliedStrategyAtEnd: 285.3
- tasksGeneratedCumulative: 9429
- tasksActivatedCumulative: 9429
- tasksRemovedAtDeadlineCumulative: 9377
- tasksPendingAtEnd: 52
- tasksPendingPeak: 86
- snapshots: 2981 / 3000
- GA jobs completed/submitted: 31 / 32
- strategyApplications: 28
- assignments LOCAL/VEHICLE/EDGE/CLOUD: 17 / 2 / 0 / 0
- violations parallel/futureSnapshot/futurePool/invalidPool/deltaTMaxMismatch: 0 / 0 / 0 / 0 / 0

## Task lifecycle note

Tasks removed at deadline are not reported as completed. The live prototype does not simulate task execution completion.

## Artifacts

- gaJobEventsJsonl: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-231224-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_ga_job_events.jsonl
- temporalStepRecordsJsonl: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-231224-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_temporal_step_records.jsonl
- detailedReportTxt: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-231224-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.txt
- detailedReportMarkdown: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-231224-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.md
- detailedReportJson: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-231224-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.json

## Errors

- none
