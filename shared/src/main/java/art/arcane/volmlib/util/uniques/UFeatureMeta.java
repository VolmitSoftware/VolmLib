package art.arcane.volmlib.util.uniques;

import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.noise.CNG;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UFeatureMeta {
    private KMap<String, CNG> generators = new KMap<>();

    public void registerGenerator(String key, CNG cng) {
        if (key != null && cng != null) {
            generators.put(key, cng);
        }
    }

    public boolean isEmpty() {
        return generators == null || generators.isEmpty();
    }
}
