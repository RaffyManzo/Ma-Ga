import io.snapshot.JsonSnapshotFolderLoader;
import model.snapshot.SystemSnapshot;
import validation.snapshot.LocalCandidateInvariantValidator;
import validation.snapshot.SnapshotValidator;

import java.util.List;

public final class LiveSnapshotValidationHarness {

    private LiveSnapshotValidationHarness() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: LiveSnapshotValidationHarness <snapshot-folder>");
        }

        JsonSnapshotFolderLoader folderLoader = new JsonSnapshotFolderLoader();
        List<SystemSnapshot> snapshots = folderLoader.load(args[0]);
        SnapshotValidator snapshotValidator = new SnapshotValidator();
        LocalCandidateInvariantValidator localValidator = new LocalCandidateInvariantValidator();
        for (SystemSnapshot snapshot : snapshots) {
            snapshotValidator.validate(snapshot);
            localValidator.validate(snapshot);
        }
        System.out.println("snapshotsLoaded=" + snapshots.size());
        System.out.println("javaLoaderValidationFailures=0");
        System.out.println("javaValidatorFailures=0");
    }
}
