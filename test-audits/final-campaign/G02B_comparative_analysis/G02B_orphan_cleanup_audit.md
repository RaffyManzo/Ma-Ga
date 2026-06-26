# Audit della fixture self-test rimossa

Durante la generazione del bundle canonico è stato rilevato un cinquantesimo `g02b_run_context.json` nella directory:

`tmp/g02b-ablation/results/self-test-bundle`

Il contesto dichiarava `runId=bundle-fixture`. Non era presente nei piani smoke o scientifico, non era referenziato nel registro e non duplicava una run pianificata. La directory apparteneva al self-test del bundler.

Prima della rimozione è stato creato il backup:

- File: `G02B_orphan_result_backup_20260626-095238.zip`.
- SHA-256: `16a4a1afe951d1bdef05eacd6a39b910c21670a625401c91070f6f1fed3e96ec`.
- Dimensione: 810 byte.

Dopo la rimozione sono rimasti esattamente 49 context, 49 validazioni di variante e 49 CSV causali. La successiva rivalidazione completa ha prodotto 43/43 self-test, 4/4 smoke e 45/45 run scientifiche valide.

Decisione: `PASS_EXPECTED_SELF_TEST_FIXTURE_CLEANUP`.
