package art.arcane.volmlib.nativelib.terrain.structure;

public interface StructureLocateSearchAccess<S, O> {
    int MAX_SELECTED_CANDIDATE_RETRIES = 64;
    StructureLocateCandidate predict();
    Verified<S, O> verify(StructureLocateCandidate candidate);
    void reject(StructureLocateCandidate candidate);
    void reference(Verified<S, O> verified);

    interface Verified<S, O> {
        S start();
        O ownership();
    }
}
