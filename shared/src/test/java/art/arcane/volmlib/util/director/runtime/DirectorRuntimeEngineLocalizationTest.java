package art.arcane.volmlib.util.director.runtime;

import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageArgumentKind;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DirectorRuntimeEngineLocalizationTest {
    @Test
    public void englishResolverRendersOnceAndValidatesArguments() {
        TextKey key = TextKey.of("director.runtime.test", "{{literal}} {value}");
        MessageArgs arguments = MessageArgs.builder()
                .untrusted("value", "{other}<red>unsafe</red>")
                .build();

        assertEquals(
                "{literal} {other}<red>unsafe</red>",
                DirectorTextResolver.ENGLISH.resolve(key, arguments)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DirectorTextResolver.ENGLISH.resolve(key, MessageArgs.empty())
        );
    }

    @Test
    public void englishDefaultsMatchExistingRuntimeMessages() {
        assertEquals(
                "This command cannot be run from this origin.",
                DirectorTextResolver.ENGLISH.resolve(DirectorRuntimeMessages.INVALID_ORIGIN)
        );
        assertEquals(
                "Unknown parameter key: mystery",
                DirectorTextResolver.ENGLISH.resolve(
                        DirectorRuntimeMessages.UNKNOWN_PARAMETER,
                        MessageArgument.untrusted("key", "mystery")
                )
        );
        assertEquals(
                "Unexpected argument \"loose\". Optional parameters must be keyed, e.g. seed=123",
                DirectorTextResolver.ENGLISH.resolve(
                        DirectorRuntimeMessages.UNEXPECTED_ARGUMENT,
                        MessageArgument.untrusted("argument", "loose")
                )
        );
        assertEquals(
                "Cannot convert \"abc\" into Integer for count",
                DirectorTextResolver.ENGLISH.resolve(
                        DirectorRuntimeMessages.CONVERSION_FAILED,
                        MessageArgument.untrusted("value", "abc"),
                        MessageArgument.untrusted("type", "Integer"),
                        MessageArgument.untrusted("parameter", "count")
                )
        );
        assertEquals(
                "Cannot parse default value for parameter count",
                DirectorTextResolver.ENGLISH.resolve(
                        DirectorRuntimeMessages.DEFAULT_PARSE_FAILED,
                        MessageArgument.untrusted("parameter", "count")
                )
        );
        assertEquals(
                "Missing argument \"count\" (Integer)",
                DirectorTextResolver.ENGLISH.resolve(
                        DirectorRuntimeMessages.MISSING_ARGUMENT,
                        MessageArgument.untrusted("parameter", "count"),
                        MessageArgument.untrusted("type", "Integer")
                )
        );
        assertEquals(
                "Failed to execute command test create: broken",
                DirectorTextResolver.ENGLISH.resolve(
                        DirectorRuntimeMessages.EXECUTION_FAILED,
                        MessageArgument.untrusted("command", "test create"),
                        MessageArgument.untrusted("reason", "broken")
                )
        );
        assertEquals(
                "Usage: test create <name>",
                DirectorTextResolver.ENGLISH.resolve(
                        DirectorRuntimeMessages.USAGE,
                        MessageArgument.untrusted("usage", "test create <name>")
                )
        );
    }

    @Test
    public void runtimeCatalogExactlyMatchesReachableMessages() {
        assertEquals(
                List.of(
                        "director.runtime.error.invalid_origin",
                        "director.runtime.error.unknown_parameter",
                        "director.runtime.error.unexpected_argument",
                        "director.runtime.error.conversion_failed",
                        "director.runtime.error.default_parse_failed",
                        "director.runtime.error.missing_argument",
                        "director.runtime.error.execution_failed",
                        "director.runtime.usage"
                ),
                DirectorRuntimeMessages.keys().stream().map(TextKey::id).toList()
        );
    }

    @Test
    public void everySenderFacingFailureUsesStructuredRuntimeMessages() {
        CapturingResolver resolver = new CapturingResolver();
        DirectorRuntimeEngine engine = DirectorEngineFactory.create(
                new LocalizedCommands(),
                DirectorEngineOptions.builder().textResolver(resolver).build()
        );
        CapturingSender sender = new CapturingSender(false);

        assertFailure(engine, sender, "playerOnly");
        assertFailure(engine, sender, "optional", "mystery=1");
        assertFailure(engine, sender, "optional", "loose");
        assertFailure(engine, sender, "number", "not-a-number");
        assertFailure(engine, sender, "brokenDefault");
        assertFailure(engine, sender, "required");
        assertFailure(engine, sender, "crash");

        Set<String> expectedKeys = Set.of(
                DirectorRuntimeMessages.INVALID_ORIGIN.id(),
                DirectorRuntimeMessages.UNKNOWN_PARAMETER.id(),
                DirectorRuntimeMessages.UNEXPECTED_ARGUMENT.id(),
                DirectorRuntimeMessages.USAGE.id(),
                DirectorRuntimeMessages.CONVERSION_FAILED.id(),
                DirectorRuntimeMessages.DEFAULT_PARSE_FAILED.id(),
                DirectorRuntimeMessages.MISSING_ARGUMENT.id(),
                DirectorRuntimeMessages.EXECUTION_FAILED.id()
        );
        assertEquals(expectedKeys, resolver.keyIds());
        assertFalse(sender.messages.isEmpty());

        for (Resolution resolution : resolver.resolutions) {
            for (MessageArgument argument : resolution.arguments.arguments().values()) {
                assertEquals(MessageArgumentKind.UNTRUSTED, argument.kind());
            }
        }
    }

    @Test
    public void invalidOriginResultCarriesExactResolvedMessage() {
        DirectorRuntimeEngine engine = DirectorEngineFactory.create(
                new LocalizedCommands(),
                DirectorEngineOptions.builder()
                        .textResolver((key, arguments) -> "localized:" + key.id())
                        .build()
        );
        CapturingSender sender = new CapturingSender(false);

        DirectorExecutionResult result = engine.execute(
                new DirectorInvocation(sender, "test", List.of("playerOnly"))
        );

        String expected = "localized:" + DirectorRuntimeMessages.INVALID_ORIGIN.id();
        assertEquals(expected, result.getMessage());
        assertEquals(List.of(expected), sender.messages);
    }

    @Test
    public void resolverIsConsultedAtExecutionTime() {
        AtomicReference<String> prefix = new AtomicReference<>("first:");
        DirectorRuntimeEngine engine = DirectorEngineFactory.create(
                new LocalizedCommands(),
                DirectorEngineOptions.builder()
                        .textResolver((key, arguments) -> prefix.get() + key.id())
                        .build()
        );
        CapturingSender sender = new CapturingSender(false);

        engine.execute(new DirectorInvocation(sender, "test", List.of("playerOnly")));
        prefix.set("second:");
        engine.execute(new DirectorInvocation(sender, "test", List.of("playerOnly")));

        assertEquals(
                List.of(
                        "first:" + DirectorRuntimeMessages.INVALID_ORIGIN.id(),
                        "second:" + DirectorRuntimeMessages.INVALID_ORIGIN.id()
                ),
                sender.messages
        );
    }

    @Test
    public void fuzzyMatchingIsStableUnderTurkishDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            DirectorRuntimeEngine engine = DirectorEngineFactory.create(new TurkishCommands());
            CapturingSender sender = new CapturingSender(false);

            DirectorExecutionResult result = engine.execute(
                    new DirectorInvocation(sender, "test", List.of("iri"))
            );

            assertTrue(result.isSuccess());
        } finally {
            Locale.setDefault(previous);
        }
    }

    private void assertFailure(
            DirectorRuntimeEngine engine,
            CapturingSender sender,
            String... arguments
    ) {
        DirectorExecutionResult result = engine.execute(
                new DirectorInvocation(sender, "test", List.of(arguments))
        );
        assertFalse(result.isSuccess());
    }

    @Director(name = "test")
    public static class LocalizedCommands {
        @Director(origin = DirectorOrigin.PLAYER)
        public void playerOnly() {
        }

        @Director
        public void optional(@Param(name = "value", defaultValue = "default") String value) {
        }

        @Director
        public void number(@Param(name = "value") int value) {
        }

        @Director
        public void brokenDefault(@Param(name = "value", defaultValue = "invalid") int value) {
        }

        @Director
        public void required(@Param(name = "value") String value) {
        }

        @Director
        public void crash() {
            throw new IllegalStateException("broken");
        }
    }

    @Director(name = "test")
    public static class TurkishCommands {
        @Director(name = "IRIS")
        public void iris() {
        }
    }

    private static final class CapturingResolver implements DirectorTextResolver {
        private final List<Resolution> resolutions = new ArrayList<>();

        @Override
        public String resolve(TextKey key, MessageArgs arguments) {
            resolutions.add(new Resolution(key, arguments));
            return "translated:" + key.id();
        }

        private Set<String> keyIds() {
            Set<String> ids = new LinkedHashSet<>();
            for (Resolution resolution : resolutions) {
                ids.add(resolution.key.id());
            }
            return Set.copyOf(ids);
        }
    }

    private static final class CapturingSender implements DirectorSender {
        private final boolean player;
        private final List<String> messages = new ArrayList<>();

        private CapturingSender(boolean player) {
            this.player = player;
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public boolean isPlayer() {
            return player;
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }
    }

    private record Resolution(TextKey key, MessageArgs arguments) {
    }
}
