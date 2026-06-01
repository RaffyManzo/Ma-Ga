package validation.snapshot;

import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;

import java.util.Objects;

/**
 * Valida l'invariante necessario al fallback locale del MA-GA.
 *
 * <p>Ogni task deve disporre di un candidato {@link NodeType#LOCAL} valido per
 * il proprio veicolo sorgente. Il repair usa questa alternativa quando una
 * scelta remota non è più sostenibile. Senza tale candidato, un fallback locale
 * rischierebbe di mantenere l'identificativo di un nodo remoto e produrre un
 * gene semanticamente incoerente.</p>
 *
 * <p>La classe è separata dal validator JSON generale perché l'invariante deve
 * poter essere riutilizzato anche da sorgenti future diverse dai file JSON,
 * incluso il bridge MOSAIC.</p>
 */
public final class LocalCandidateInvariantValidator {

    /**
     * Verifica che ogni task abbia il proprio candidato locale.
     *
     * @param snapshot snapshot da validare
     * @throws IllegalArgumentException se almeno un task non dispone del
     *                                  fallback locale richiesto
     */
    public void validate(SystemSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        for (TaskInstance task : snapshot.getTasks()) {
            if (!hasLocalCandidate(snapshot, task)) {
                throw new IllegalArgumentException(
                        "Snapshot " + snapshot.getSnapshotId()
                                + " does not define a LOCAL candidate for task "
                                + task.getTaskId()
                                + " and source vehicle "
                                + task.getSourceVehicleId()
                );
            }
        }
    }

    private boolean hasLocalCandidate(SystemSnapshot snapshot, TaskInstance task) {
        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            if (candidate.getType() == NodeType.LOCAL
                    && candidate.isValidForSourceVehicle(task.getSourceVehicleId())) {
                return true;
            }
        }
        return false;
    }
}
