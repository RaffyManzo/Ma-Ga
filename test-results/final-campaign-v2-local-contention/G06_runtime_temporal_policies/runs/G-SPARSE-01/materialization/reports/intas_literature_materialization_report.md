# Synthetic-Calibrated InTAS Literature Scenario

- status: `SYNTHETIC_CALIBRATED_SCENARIO_GENERATED`
- mobility mode: `SYNTHETIC_CALIBRATED_ON_INTAS_SUBNETWORK`
- topology source: `https://github.com/silaslobo/InTAS`
- selected subnetwork: `candidate_0045`
- external edges: `155`
- external junctions: `88`
- traffic lights: `8`

## Nominal synthetic mobility validation

- generated vehicles: `29`
- mean active vehicles: `18.12`
- maximum active vehicles: `26`
- vehicles visiting both RSUs: `13`
- gateway-switch events: `10`
- SUMO errors: `0`
- teleport mentions: `0`
- emergency-braking mentions: `0`

## Notes

The scenario keeps the validated InTAS urban topology but generates deterministic synthetic demand. It intentionally avoids replay of an intermediate InTAS save-state.
