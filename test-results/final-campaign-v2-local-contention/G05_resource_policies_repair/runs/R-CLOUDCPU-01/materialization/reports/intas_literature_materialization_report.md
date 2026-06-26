# Synthetic-Calibrated InTAS Literature Scenario

- status: `SYNTHETIC_CALIBRATED_SCENARIO_GENERATED`
- mobility mode: `SYNTHETIC_CALIBRATED_ON_INTAS_SUBNETWORK`
- topology source: `https://github.com/silaslobo/InTAS`
- selected subnetwork: `candidate_0045`
- external edges: `155`
- external junctions: `88`
- traffic lights: `8`

## Nominal synthetic mobility validation

- generated vehicles: `50`
- mean active vehicles: `31.22`
- maximum active vehicles: `46`
- vehicles visiting both RSUs: `22`
- gateway-switch events: `18`
- SUMO errors: `0`
- teleport mentions: `0`
- emergency-braking mentions: `0`

## Notes

The scenario keeps the validated InTAS urban topology but generates deterministic synthetic demand. It intentionally avoids replay of an intermediate InTAS save-state.
