package art.arcane.volmlib.util.collection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Pair<A, B> {
    private A a;
    private B b;
}
