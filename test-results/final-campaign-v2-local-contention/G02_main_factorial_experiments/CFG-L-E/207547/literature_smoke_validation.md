# Literature Scenario Smoke Validation

- status: LITERATURE_SMOKE_TEST_PASSED
- run: log-20260626-230013-MaGaLiteratureBasedUrbanStudy
- scenario: MaGaLiteratureBasedUrbanStudy
- simulationCompleted: True
- taskCountersSource: LIVE_MAGA_RUNTIME_COORDINATOR_TICK
- taskCompletionModel: NOT_IMPLEMENTED
- gaParameterScalingMode: STATIC
- gaRuntimeMeanSeconds: 0.188347498
- gaRuntimeMedianSeconds: 0.0013787
- gaRuntimeP95Seconds: 1.5920694
- gaRuntimeMaxSeconds: 2.9138537
- staleRatioPercent: 17.857143
- staleSequenceCount: 6
- longestConsecutiveStaleSequence: 4
- maximumAbsoluteSnapshotLagSeconds: 0
- nonZeroLagWindowCount: 0
- lastAppliedStrategySimulationTimeSeconds: 241.4
- secondsWithoutAppliedStrategyAtEnd: 58.6
- tasksGeneratedCumulative: 5365
- tasksActivatedCumulative: 5365
- tasksRemovedAtDeadlineCumulative: 5338
- tasksPendingAtEnd: 27
- tasksPendingPeak: 52
- snapshots: 2981 / 3000
- GA jobs completed/submitted: 56 / 57
- strategyApplications: 46
- assignments LOCAL/VEHICLE/EDGE/CLOUD: 105 / 1 / 0 / 2
- violations parallel/futureSnapshot/futurePool/invalidPool/deltaTMaxMismatch: 0 / 0 / 0 / 0 / 0

## Task lifecycle note

Tasks removed at deadline are not reported as completed. The live prototype does not simulate task execution completion.

## Artifacts

- gaJobEventsJsonl: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-230013-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_ga_job_events.jsonl
- temporalStepRecordsJsonl: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-230013-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_temporal_step_records.jsonl
- detailedReportTxt: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-230013-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.txt
- detailedReportMarkdown: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-230013-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.md
- detailedReportJson: C:\Users\raffa\IdeaProjects\maga-core\tmp\mosaic-25.2\logs\log-20260626-230013-MaGaLiteratureBasedUrbanStudy\live-maga-runtime\live-reporting\live_detailed_execution_report.json

## Errors

- none
