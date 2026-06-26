# Literature Scenario Smoke Validation

- status: LITERATURE_SMOKE_TEST_PASSED
- run: log-20260626-230817-MaGaLiteratureBasedUrbanStudy
- scenario: MaGaLiteratureBasedUrbanStudy
- simulationCompleted: True
- taskCountersSource: LIVE_MAGA_RUNTIME_COORDINATOR_TICK
- taskCompletionModel: NOT_IMPLEMENTED
- gaParameterScalingMode: STATIC
- gaRuntimeMeanSeconds: 0.253398714
- gaRuntimeMedianSeconds: 0.0012978
- gaRuntimeP95Seconds: 1.770917
- gaRuntimeMaxSeconds: 3.7174286
- staleRatioPercent: 17.142857
- staleSequenceCount: 2
- longestConsecutiveStaleSequence: 4
- maximumAbsoluteSnapshotLagSeconds: 0
- nonZeroLagWindowCount: 0
- lastAppliedStrategySimulationTimeSeconds: 29.2
- secondsWithoutAppliedStrategyAtEnd: 270.8
- tasksGeneratedCumulative: 9429
- tasksActivatedCumulative: 9429
- tasksRemovedAtDeadlineCumulative: 9386
- tasksPendingAtEnd: 43
- tasksPendingPeak: 71
- snapshots: 2981 / 3000
- GA jobs completed/submitted: 35 / 36
- strategyApplications: 29
- assignments LOCAL/VEHICLE/EDGE/CLOUD: 22 / 1 / 0 / 0
- violations parallel/futureSnapshot/futurePool/invalidPool/deltaTMaxMismatch: 0 / 0 / 0 / 0 / 0

## Task lifecycle note

Tasks removed at deadline are not reported as completed. The live prototype does not simulate task execution completion.

## Artifacts

- gaJobEventsJsonl: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-230817-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_ga_job_events.jsonl
- temporalStepRecordsJsonl: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-230817-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_temporal_step_records.jsonl
- detailedReportTxt: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-230817-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.txt
- detailedReportMarkdown: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-230817-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.md
- detailedReportJson: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-230817-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.json

## Errors

- none
