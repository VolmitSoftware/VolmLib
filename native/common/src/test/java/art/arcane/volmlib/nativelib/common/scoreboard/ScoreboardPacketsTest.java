package art.arcane.volmlib.nativelib.common.scoreboard;

import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.assertArrayEquals;

public class ScoreboardPacketsTest {
    @Test
    public void detachedObjectiveArgumentsCoverLegacyAndModernConstructors() throws Exception {
        Class<?> packetBridgeClass = ReflectiveScoreboardPackets.class;
        Method argumentsMethod = packetBridgeClass.getDeclaredMethod(
                "objectiveConstructorArguments",
                int.class,
                Object.class,
                String.class,
                Object.class,
                Object.class,
                Object.class,
                Object.class
        );
        argumentsMethod.setAccessible(true);

        Object scoreboard = new Object();
        Object criteria = new Object();
        Object component = new Object();
        Object renderType = new Object();
        Object numberFormat = new Object();

        Object[] legacy = (Object[]) argumentsMethod.invoke(
                null,
                5,
                scoreboard,
                "objective",
                criteria,
                component,
                renderType,
                numberFormat
        );
        Object[] modern = (Object[]) argumentsMethod.invoke(
                null,
                7,
                scoreboard,
                "objective",
                criteria,
                component,
                renderType,
                numberFormat
        );

        assertArrayEquals(new Object[]{scoreboard, "objective", criteria, component, renderType}, legacy);
        assertArrayEquals(
                new Object[]{scoreboard, "objective", criteria, component, renderType, Boolean.TRUE, numberFormat},
                modern
        );
    }

}
