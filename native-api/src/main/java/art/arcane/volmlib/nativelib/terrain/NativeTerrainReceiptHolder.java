package art.arcane.volmlib.nativelib.terrain;

public interface NativeTerrainReceiptHolder {
    long volmlib$getStructureActivation();

    void volmlib$setStructureActivation(long activation);

    byte[] volmlib$getNaturalTerrain();

    void volmlib$setNaturalTerrain(byte[] receipt);
}
