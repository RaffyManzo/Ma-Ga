# Local Cleanup Report

Created: 2026-06-06T12:20:28.3066737+02:00

Deleted local/generated files:

- data/mosaic-scenarios/MaGaLiveMagaRuntimeStudy/application/maga-live-maga-runtime.jar removed from the versioned scenario. It is regenerated in tools/mosaic-live-maga-runtime/out/ and injected into tmp/ during deploy.

Local generated directories kept:

- tmp/mosaic-25.2/ including recent live runs and baseline evidence.
- tools/mosaic-live-maga-runtime/out/ because deploy consumes the generated JAR after build.

No tmp/mosaic-25.2/logs/ run directories were deleted.

