# Literature Scenario Smoke Validation

- status: LITERATURE_SMOKE_TEST_FAILED
- run: log-20260627-015313-MaGaLiteratureBasedUrbanStudy
- scenario: MaGaLiteratureBasedUrbanStudy
- simulationCompleted: True
- taskCountersSource: LIVE_MAGA_RUNTIME_COORDINATOR_TICK
- taskCompletionModel: NOT_IMPLEMENTED
- gaParameterScalingMode: STATIC
- gaRuntimeMeanSeconds: 1.1381507
- gaRuntimeMedianSeconds: 0.3902588
- gaRuntimeP95Seconds: 3.7471522
- gaRuntimeMaxSeconds: 3.7471522
- staleRatioPercent: 100
- staleSequenceCount: 1
- longestConsecutiveStaleSequence: 5
- maximumAbsoluteSnapshotLagSeconds: 0
- nonZeroLagWindowCount: 0
- lastAppliedStrategySimulationTimeSeconds: 0
- secondsWithoutAppliedStrategyAtEnd: 0
- tasksGeneratedCumulative: 5640
- tasksActivatedCumulative: 5640
- tasksRemovedAtDeadlineCumulative: 5606
- tasksPendingAtEnd: 34
- tasksPendingPeak: 79
- snapshots: 1781 / 1800
- GA jobs completed/submitted: 5 / 6
- strategyApplications: 0
- assignments LOCAL/VEHICLE/EDGE/CLOUD: 0 / 0 / 0 / 0
- violations parallel/futureSnapshot/futurePool/invalidPool/deltaTMaxMismatch: 0 / 0 / 0 / 0 / 0

## Task lifecycle note

Tasks removed at deadline are not reported as completed. The live prototype does not simulate task execution completion.

## Artifacts

- gaJobEventsJsonl: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260627-015313-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_ga_job_events.jsonl
- temporalStepRecordsJsonl: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260627-015313-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_temporal_step_records.jsonl
- detailedReportTxt: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260627-015313-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.txt
- detailedReportMarkdown: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260627-015313-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.md
- detailedReportJson: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260627-015313-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.json

## Errors

- strategyApplications <= 0
