# VolmLib PlaceholderAPI expansion base

`art.arcane.volmlib.util.bukkit.papi` is the base every Volmit plugin builds its PlaceholderAPI expansion on.
It exists because PlaceholderAPI calls your code from an unknown thread, with no exception handling of its
own, in the middle of somebody else's string formatting — and a placeholder expansion that reads live server
state from there is a crash waiting for the right scoreboard plugin. The package gives you four pieces: a
final, total `onRequest` implementation; a registry that maps placeholder paths to resolvers; two snapshot
holders that let the owning thread publish data the resolver can read from anywhere; and a value formatter
whose output is safe to hand back to PlaceholderAPI.

`art.arcane.volmlib.util.bukkit.Placeholders` is the other side of the same coin: resolving `%...%` inside
text your plugin is about to show, when PlaceholderAPI may or may not be installed.

| You want to…                                                | Use                                                                  |
|-------------------------------------------------------------|----------------------------------------------------------------------|
| publish `%yourplugin_something%` for other plugins to consume | `VolmitPlaceholderExpansion` + `PlaceholderKeyRegistry`             |
| resolve `%...%` in a string before you display it            | `Placeholders.setPlaceholders(Player, String)`                       |

---

## Depending on it

VolmLib itself is shaded into your jar — see [README.md](README.md) for the dependency block and the
relocation rule. PlaceholderAPI is `compileOnly`; the server provides it.

```groovy
repositories {
    maven {
        url = uri('https://repo.extendedclip.com/content/repositories/placeholderapi/')
    }
}

dependencies {
    compileOnly('me.clip:placeholderapi:2.11.6') { transitive = false }
}
```

Bukkit plugin (`plugin.yml`):

```yaml
softdepend: [ PlaceholderAPI ]
```

Paper plugin (`paper-plugin.yml`):

```yaml
dependencies:
  server:
    PlaceholderAPI:
      load: BEFORE
      required: false
      join-classpath: true
```

`join-classpath: true` is mandatory on Paper. Plugin classloaders are isolated there, and without it your
subclass cannot see `me.clip.placeholderapi.expansion.PlaceholderExpansion` and fails to link.

### This surface is relocation-safe

`VolmitPlaceholderExpansion` is one of the very few VolmLib types that legitimately faces another plugin. It
is safe because the only name crossing the jar boundary is `PlaceholderExpansion`, which belongs to
PlaceholderAPI and is never relocated, and because the value you return is a `java.lang.String`. Your copy of
the base class, the registry, the snapshot holders and the formatter never leave your jar. Two plugins that
shade VolmLib into different packages can both register an expansion on the same server and neither is aware
of the other. The full rule is in [README.md](README.md#the-relocation-rule).

---

## The lifecycle

```
build a PlaceholderKeyRegistry            declare every key and group up front. Immutable once built.
   |
   v
extend VolmitPlaceholderExpansion         pass identifier, author, version, required plugin, registry, logger
   |
   v
PlaceholderRegistration.register(factory) builds and registers the expansion, if PlaceholderAPI is enabled
   |
   v
PlaceholderAPI calls onRequest(...)       once per %identifier_path% occurrence, on the caller's thread
   |
   v
PlaceholderRegistration.unregister()      on plugin disable
```

What the base guarantees, in order:

- The registry is **immutable**. `PlaceholderKeyRegistry.Builder.build()` copies both maps; there is no way to
  add a key afterwards. Everything you might ever answer has to be declared at construction time.
- Construction is **validated**. The identifier must be non-empty and match `[a-z0-9]` — no underscores, no
  uppercase — because PlaceholderAPI splits `%identifier_path%` at the first underscore and an identifier
  containing one could never be dispatched. The registry must publish the reserved key `available`. Both
  failures throw `IllegalArgumentException` from the constructor. Every reference argument is null-checked.
- `onRequest` is **final** and is the **only** exception barrier in the whole path.
- `persist()` returns `true`, so PlaceholderAPI does not drop your expansion when an administrator reloads it.
  The registration lives exactly as long as you keep it.
- `getPlaceholders()` returns the registry's published key list, sorted, with groups shown as `<group>.*`.

### Why the barrier has to be there

PlaceholderAPI does not guard its own call site. Its replacer looks up the expansion by identifier and invokes
`onRequest` inline, with no `try`/`catch` anywhere in the method. Whatever your resolver throws propagates
into the stack of whoever asked for the string — a scoreboard plugin's repeating task, a chat listener, a
hologram renderer, a web panel's async request. A single `NullPointerException` in a placeholder resolver
routinely takes down an unrelated plugin's tick loop.

`VolmitPlaceholderExpansion.onRequest` catches `Throwable`, not `Exception`. `StackOverflowError` from an
accidental recursion and `NoClassDefFoundError` from an optional dependency are caught along with the ordinary
mistakes. It returns `PlaceholderValues.UNAVAILABLE` — the string `---` — and logs the failure once.

The logging is deliberately bounded: one `WARNING` with the stack trace per **distinct** path, and at most 64
distinct paths for the lifetime of the expansion object. A resolver that throws on every request logs once and
then stays quiet; a group resolver that throws for unbounded generated paths cannot fill the log either. Past
64 distinct paths, failures are silent and still return `---`.

The barrier does **not** quarantine anything. A resolver that throws is called again on the next request,
every time, forever. There is no fault counter and no disable threshold.

---

## Threading

**`onRequest` arrives on whatever thread called PlaceholderAPI.** There is no hop, no queue and no promise.
In practice that means the global region thread, a region thread, an entity scheduler thread, an async task,
or a netty thread, decided entirely by a third-party plugin you have never heard of. On Folia, several of
those are live at once.

The consequence is absolute:

> **A resolver must not touch Bukkit state.** No `Bukkit.getPlayer(...)`, no `World`, no `Entity`, no
> `Location`, no chunk access, no inventory, no scoreboard. No blocking either — no I/O, no lock you also take
> elsewhere, no `CompletableFuture.join`.

A resolver is a field read and a string return. That is the whole budget. It runs once per placeholder
occurrence per string, and a busy scoreboard plugin will call it several times a second for every online
player.

Everything else in the package is built so that this restriction is survivable:

| Call                                                   | Thread                                                                       |
|--------------------------------------------------------|-------------------------------------------------------------------------------|
| `PlaceholderKeyRegistry.Resolver.resolve(UUID)`        | Unknown. Read published snapshots only                                        |
| `PlaceholderKeyRegistry.GroupResolver.resolve(UUID, String)` | Unknown. Same rule                                                      |
| `PlaceholderSnapshot.publish` / `get` / `available`     | Any thread. Backed by `volatile` fields                                      |
| `PlayerSnapshotStore.publish` / `get` / `evictAfterGrace` / `clear` | Any thread. Backed by a `ConcurrentHashMap`                      |
| `PlaceholderValues.*`                                   | Any thread. Pure functions over their arguments                              |
| `PlaceholderRegistration.register` / `unregister`       | Main (global region) thread only — see below                                 |
| `PlaceholderRegistration.isPlaceholderApiEnabled`       | Main (global region) thread. It reads the plugin manager, which is not documented as thread-safe |
| `PlaceholderRegistration.isRegistered`                  | Any thread. A single `volatile` read, and stale the instant it returns       |
| `Placeholders.setPlaceholders`                          | The thread that owns the player, because third-party expansions may not be safe |

`PlaceholderRegistration` holds its expansion in a `volatile` field but does its "already registered?" check
and its assignment as two separate steps. Two threads calling `register` at once can both build and both
register. Call it from `onEnable`, `onDisable` and your `PluginEnableEvent` handler — all of which run on the
main thread — and the race cannot happen.

**The snapshot object you publish must be immutable.** The store publishes the reference safely; it cannot
make the object safe. Publish a record with final fields whose values are already formatted strings, and never
mutate it after handing it over. Formatting on the publisher thread is the point: it moves the work off the
unknown-thread read path and leaves the resolver as a field read.

---

## Worked example: a claims plugin

A land-claim plugin exposing server totals, the claim the player is standing in, and per-flag lookups. It
publishes once a second from the global region thread, and per player from each player's own entity scheduler.

### The snapshots

Both snapshots are records of pre-formatted strings. Nothing in them needs interpreting at read time.

```java
package com.example.claims.papi;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;

import java.util.LinkedHashMap;
import java.util.Map;

public record ClaimsRuntimeSnapshot(String claims, String owners, Map<String, String> flagDefaults) {
    public static ClaimsRuntimeSnapshot of(int claims, int owners, Map<String, Boolean> flagDefaults) {
        Map<String, String> formatted = new LinkedHashMap<>(flagDefaults.size());

        for (Map.Entry<String, Boolean> entry : flagDefaults.entrySet()) {
            formatted.put(entry.getKey(), PlaceholderValues.bool(entry.getValue()));
        }

        return new ClaimsRuntimeSnapshot(
            PlaceholderValues.count(claims),
            PlaceholderValues.count(owners),
            Map.copyOf(formatted));
    }

    public String flagDefault(String flagId) {
        return flagDefaults.get(flagId);
    }
}
```

```java
package com.example.claims.papi;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;

import java.util.LinkedHashMap;
import java.util.Map;

public record ClaimsPlayerSnapshot(String name, String owner, String area, String trusted, Map<String, String> flags) {
    public static ClaimsPlayerSnapshot of(String name, String owner, int area, boolean trusted, Map<String, Boolean> flags) {
        Map<String, String> formatted = new LinkedHashMap<>(flags.size());

        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            formatted.put(entry.getKey(), PlaceholderValues.bool(entry.getValue()));
        }

        return new ClaimsPlayerSnapshot(
            PlaceholderValues.text(name),
            PlaceholderValues.text(owner),
            PlaceholderValues.count(area),
            PlaceholderValues.bool(trusted),
            Map.copyOf(formatted));
    }

    public String flag(String flagId) {
        return flags.get(flagId);
    }
}
```

`Map.copyOf` matters as much as the formatting does. The map goes to threads you do not control.

### The expansion

The subclass is metadata and nothing else. `onRequest`, `getIdentifier`, `getAuthor`, `getVersion`,
`getRequiredPlugin`, `getPlaceholders` and `persist` are all `final` on the base, so the resolution path
cannot be overridden or partially reimplemented. The handful of `PlaceholderExpansion` methods the base does
not touch — `canRegister()`, `getName()`, `getPlugin()`, `getDescription()` and `getLink()` — stay
overridable, and PlaceholderAPI really does call the first two while registering. Overriding `canRegister()`
to return `false` is the one remaining way a subclass can stop its own registration.

```java
package com.example.claims.papi;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderKeyRegistry;
import art.arcane.volmlib.util.bukkit.papi.VolmitPlaceholderExpansion;

import java.util.logging.Logger;

public final class ClaimsPlaceholderExpansion extends VolmitPlaceholderExpansion {
    public static final String IDENTIFIER = "claims";
    public static final String AUTHOR = "Example";
    public static final String REQUIRED_PLUGIN = "Claims";

    public ClaimsPlaceholderExpansion(String version, PlaceholderKeyRegistry keys, Logger logger) {
        super(IDENTIFIER, AUTHOR, version, REQUIRED_PLUGIN, keys, logger);
    }
}
```

### The registry and the snapshot holders

```java
package com.example.claims.papi;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderKeyRegistry;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderRegistration;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderSnapshot;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;
import art.arcane.volmlib.util.bukkit.papi.PlayerSnapshotStore;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;

public final class ClaimsPlaceholders {
    private final PlaceholderSnapshot<ClaimsRuntimeSnapshot> runtime = new PlaceholderSnapshot<>();
    private final PlayerSnapshotStore<ClaimsPlayerSnapshot> players = new PlayerSnapshotStore<>();
    private final PlaceholderKeyRegistry keys = registry(runtime, players);
    private final PlaceholderRegistration registration;
    private final String version;
    private final Logger logger;

    public ClaimsPlaceholders(String version, Logger logger) {
        this.version = Objects.requireNonNull(version, "version");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.registration = new PlaceholderRegistration(this.logger);
    }

    public boolean register() {
        return registration.register(() -> new ClaimsPlaceholderExpansion(version, keys, logger));
    }

    public void unregister() {
        registration.unregister();
        players.clear();
        runtime.publish(null);
    }

    public void publishRuntime(ClaimsRuntimeSnapshot snapshot) {
        runtime.publish(snapshot);
    }

    public void publishPlayer(UUID playerId, ClaimsPlayerSnapshot snapshot) {
        players.publish(playerId, snapshot);
    }

    public void forget(UUID playerId) {
        players.evictAfterGrace(playerId, PlayerSnapshotStore.DEFAULT_GRACE_MS);
    }

    private static PlaceholderKeyRegistry registry(
        PlaceholderSnapshot<ClaimsRuntimeSnapshot> runtime,
        PlayerSnapshotStore<ClaimsPlayerSnapshot> players) {
        return PlaceholderKeyRegistry.builder()
            .key(PlaceholderKeyRegistry.AVAILABLE, playerId -> runtime.available())
            .key("claims", playerId -> runtimeValue(runtime, ClaimsRuntimeSnapshot::claims))
            .key("owners", playerId -> runtimeValue(runtime, ClaimsRuntimeSnapshot::owners))
            .key("claim.available", players::available)
            .key("claim.name", playerId -> playerValue(players, playerId, ClaimsPlayerSnapshot::name))
            .key("claim.owner", playerId -> playerValue(players, playerId, ClaimsPlayerSnapshot::owner))
            .key("claim.area", playerId -> playerValue(players, playerId, ClaimsPlayerSnapshot::area))
            .key("claim.trusted", playerId -> playerValue(players, playerId, ClaimsPlayerSnapshot::trusted))
            .group("flag", (playerId, tail) -> flag(runtime, players, playerId, tail))
            .build();
    }

    private static String runtimeValue(
        PlaceholderSnapshot<ClaimsRuntimeSnapshot> runtime,
        Function<ClaimsRuntimeSnapshot, String> field) {
        ClaimsRuntimeSnapshot snapshot = runtime.get();
        return snapshot == null ? PlaceholderValues.UNAVAILABLE : field.apply(snapshot);
    }

    private static String playerValue(
        PlayerSnapshotStore<ClaimsPlayerSnapshot> players,
        UUID playerId,
        Function<ClaimsPlayerSnapshot, String> field) {
        ClaimsPlayerSnapshot snapshot = players.get(playerId);
        return snapshot == null ? PlaceholderValues.UNAVAILABLE : field.apply(snapshot);
    }

    private static String flag(
        PlaceholderSnapshot<ClaimsRuntimeSnapshot> runtime,
        PlayerSnapshotStore<ClaimsPlayerSnapshot> players,
        UUID playerId,
        String tail) {
        int separator = tail.indexOf('.');
        String flagId = separator < 0 ? tail : tail.substring(0, separator);
        String argument = separator < 0 ? "" : tail.substring(separator + 1);
        ClaimsRuntimeSnapshot server = runtime.get();

        if (server == null || server.flagDefault(flagId) == null) {
            return null;
        }

        return switch (argument) {
            case "" -> playerFlag(players, playerId, flagId, server);
            case "default" -> server.flagDefault(flagId);
            default -> null;
        };
    }

    private static String playerFlag(
        PlayerSnapshotStore<ClaimsPlayerSnapshot> players,
        UUID playerId,
        String flagId,
        ClaimsRuntimeSnapshot server) {
        ClaimsPlayerSnapshot snapshot = players.get(playerId);

        if (snapshot == null) {
            return PlaceholderValues.UNAVAILABLE;
        }

        String value = snapshot.flag(flagId);
        return value == null ? server.flagDefault(flagId) : value;
    }
}
```

Read the `flag` group resolver carefully — it is the whole argument-parsing contract in one method. An unknown
flag returns `null`, so `%claims_flag.nonsense%` survives as a literal and the administrator sees the typo. A
known flag with no snapshot returns `---`, because the key is real and the data simply is not there yet. An
unrecognised argument returns `null` for the same reason as the unknown flag.

### Registration and publishing

```java
package com.example.claims.papi;

import com.example.claims.ClaimIndex;
import com.example.claims.ClaimsPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.Objects;
import java.util.UUID;

public final class ClaimsPlaceholderService implements Listener {
    private static final String PLACEHOLDER_API = "PlaceholderAPI";
    private static final long PUBLISH_PERIOD_TICKS = 20L;

    private final ClaimsPlugin plugin;
    private final ClaimsPlaceholders placeholders;
    private ScheduledTask publishTask;

    public ClaimsPlaceholderService(ClaimsPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.placeholders = new ClaimsPlaceholders(plugin.getPluginMeta().getVersion(), plugin.getLogger());
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        placeholders.register();
        publishTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            plugin, task -> publish(), PUBLISH_PERIOD_TICKS, PUBLISH_PERIOD_TICKS);
    }

    public void stop() {
        if (publishTask != null) {
            publishTask.cancel();
            publishTask = null;
        }

        HandlerList.unregisterAll(this);
        placeholders.unregister();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (PLACEHOLDER_API.equals(event.getPlugin().getName())) {
            placeholders.register();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        placeholders.forget(event.getPlayer().getUniqueId());
    }

    private void publish() {
        ClaimIndex index = plugin.claimIndex();
        placeholders.publishRuntime(ClaimsRuntimeSnapshot.of(
            index.totalClaims(), index.totalOwners(), index.flagDefaults()));

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            player.getScheduler().run(plugin, task -> placeholders.publishPlayer(
                playerId, index.snapshotAt(player.getLocation())), null);
        }
    }
}
```

`ClaimIndex` is the plugin's own state; only its shape matters here — `totalClaims()`, `totalOwners()`,
`flagDefaults()` and `snapshotAt(Location)`.

Four things in that class are the pattern, not decoration:

- **The `PluginEnableEvent` handler.** `register()` returns `false` when PlaceholderAPI is not enabled yet and
  never retries by itself. Without this handler, a server where PlaceholderAPI loads after your plugin has no
  placeholders at all until the next restart. Calling `register()` twice is harmless: it returns `true`
  immediately once an expansion is held.
- **The global region scheduler for server-wide data.** Totals belong to no region, so they are gathered on
  the thread that owns nothing in particular, once a second. One second is a deliberate choice: a read is a
  field lookup, so publishing more often buys the consumer nothing and costs the server a pass over every
  player.
- **The entity scheduler for per-player data.** `player.getLocation()` may only be read from the thread that
  owns that player. `Bukkit.getOnlinePlayers()` is enumerated on the global region thread, then each snapshot
  is built on that player's own scheduler and published from there. The `null` third argument is the retired
  callback: nothing to do when the player has left, because the quit handler already cleans up. Whatever the
  snapshot is built from — `ClaimIndex` here — has to be safe to read from that thread too; the store fixes
  the hand-off, not your own data structures.
- **`forget` on quit, `clear` on disable.** `forget` calls `evictAfterGrace`, which keeps serving the last
  snapshot for `DEFAULT_GRACE_MS` so a death screen or a quit message rendered a tick later still resolves,
  then stops. `stop()` reaches `ClaimsPlaceholders.unregister()`, which clears the store outright.

### Reading it back

```
%claims_available%          true
%claims_claims%             412
%claims_claim.name%         Riverbend
%claims_claim.area%         2048
%claims_flag.pvp%           false
%claims_flag.pvp.default%   true
%claims_flag.nonsense%      %claims_flag.nonsense%
```

Paths are case-insensitive: `onRequest` lowercases the entire parameter string with `Locale.ROOT` before it
reaches the registry, so `%claims_Claim.Name%` resolves identically.

---

## The minimum: server-scoped values only

If none of your values are per-player, you need no snapshot store and no publisher. A supplier of your own
runtime state is enough, and the expansion is one class.

```java
package com.example.tiny;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderKeyRegistry;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;
import art.arcane.volmlib.util.bukkit.papi.VolmitPlaceholderExpansion;

import java.util.function.Supplier;
import java.util.logging.Logger;

public final class TinyExpansion extends VolmitPlaceholderExpansion {
    public TinyExpansion(Supplier<TinyState> state, Logger logger) {
        super("tiny", "Example", "1.0.0", "Tiny", registry(state), logger);
    }

    private static PlaceholderKeyRegistry registry(Supplier<TinyState> state) {
        return PlaceholderKeyRegistry.builder()
            .key(PlaceholderKeyRegistry.AVAILABLE, playerId -> PlaceholderValues.bool(state.get() != null))
            .key("regions", playerId -> {
                TinyState current = state.get();
                return current == null ? PlaceholderValues.UNAVAILABLE : PlaceholderValues.count(current.regions());
            })
            .key("load", playerId -> {
                TinyState current = state.get();
                return current == null ? PlaceholderValues.UNAVAILABLE : PlaceholderValues.percent(current.load());
            })
            .build();
    }
}
```

```java
package com.example.tiny;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderRegistration;
import org.bukkit.plugin.java.JavaPlugin;

public final class TinyPlugin extends JavaPlugin {
    private PlaceholderRegistration placeholders;
    private volatile TinyState state;

    @Override
    public void onEnable() {
        placeholders = new PlaceholderRegistration(getLogger());
        placeholders.register(() -> new TinyExpansion(this::stateOrNull, getLogger()));
    }

    @Override
    public void onDisable() {
        if (placeholders != null) {
            placeholders.unregister();
            placeholders = null;
        }
    }

    private TinyState stateOrNull() {
        return state;
    }
}
```

`TinyState` is a record of plain values, and the field holding it is `volatile` because the resolver reads it
from an unknown thread. That `volatile` is not optional — it is the entire concurrency design of this
expansion. Everything the resolver reads must be published safely, whether through a `PlaceholderSnapshot` or
through a field you declared correctly yourself.

This version registers only in `onEnable`, so it produces nothing on a server where PlaceholderAPI enables
after it. Add the `PluginEnableEvent` handler from the worked example if that matters to you; it is four lines
and it is the difference between working everywhere and working on your own test server.

---

## What the resolver is told

```java
@FunctionalInterface
public interface Resolver {
    String resolve(UUID playerId);
}

@FunctionalInterface
public interface GroupResolver {
    String resolve(UUID playerId, String tail);
}
```

That is the entire context. A `UUID` or `null`, and for a group the rest of the path.

`onRequest` receives an `org.bukkit.OfflinePlayer` from PlaceholderAPI and calls exactly one method on it —
`getUniqueId()`. Nothing else is read, and the `OfflinePlayer` reference is never passed on. This is why the
resolver signature takes a `UUID`: an `OfflinePlayer` handed to you on an arbitrary thread is not a safe
object to hold, and resolving it back to a `Player` from that thread is exactly the mistake this package
exists to prevent.

`playerId` is `null` when PlaceholderAPI resolves a placeholder with no player context — console output,
scheduled broadcasts, a web panel. Server-scoped keys should ignore the argument entirely; player-scoped keys
should return `PlaceholderValues.UNAVAILABLE` for a null id rather than throwing.

---

## Key registry reference

```java
public static PlaceholderKeyRegistry.Builder builder()

public Builder key(String key, Resolver resolver)
public Builder group(String group, GroupResolver resolver)
public PlaceholderKeyRegistry build()

public String resolve(UUID playerId, String path)
public boolean containsKey(String key)
public List<String> keys()
```

### The key grammar

Enforced by the builder, inside `key(...)` and `group(...)` as you declare each one — not deferred to
`build()` — so a malformed key is a startup failure and not a runtime surprise.

| Rule                                                  | Rejected example      |
|-------------------------------------------------------|-----------------------|
| Segments of `[a-z0-9-]` separated by `.`               | `can_claim_next`      |
| Lowercase only                                        | `Player.Level`        |
| No empty segment                                      | `player..level`       |
| No leading or trailing `.`                             | `player.level.`       |
| At most four segments                                  | `a.b.c.d.e`           |
| Not empty                                             | `""`                  |
| A group name must be a single segment                  | `group("skill.mining")` |
| No duplicate key, no duplicate group                   | two `key("player.level")` |

Violations throw `IllegalArgumentException`; a null key, group or resolver throws `NullPointerException`.

Hyphens are the word separator — `xp-to-next`, `cross-server`, `has-level`. Underscores are not permitted
anywhere in a key, because PlaceholderAPI has already used the first underscore to separate the identifier
from the path.

### Exact keys and group keys

`resolve` does two lookups and stops:

1. The full path against the exact-key map. A hit calls that `Resolver` and returns.
2. Otherwise, if any group is declared, the path is split at its **first** `.`. The text before it names the
   group, the text after it becomes the `tail`. A known group calls its `GroupResolver`.

Anything else returns `null`.

**An exact key always wins.** With `key("skill.count")` and `group("skill")` both declared, `skill.count`
never reaches the group resolver — it is not consulted at all. That is the intended way to carve a fixed key
out of a dynamic namespace.

Malformed paths never reach a group. Step 2 refuses a path with no `.`, a path starting with `.`, and a path
ending with `.`, so `skill`, `.skill` and `skill.` all return `null` without any group resolver running. Only
step 2 is affected: a dotless path is still a perfectly good exact key, which is how `available` and `claims`
resolve at all.

### Arguments

The registry does not parse arguments. It hands the group resolver the entire tail and gets out of the way,
which is why the `flag` resolver above splits its own tail. The same technique nests: split the tail at its
first `.` for the subject, then at its last `.` for a trailing numeric argument, and dispatch on what is left
with a `switch` expression.

Two properties of the tail are easy to get wrong:

- **The four-segment cap does not apply to it.** That limit is a builder rule for *declared* keys. `resolve`
  applies no grammar check to an incoming path, so `%claims_flag.a.b.c.d.e.f%` reaches the `flag` group with
  the tail `a.b.c.d.e.f`.
- **It is untrusted input.** It is whatever PlaceholderAPI found after the identifier, lowercased. It can be
  empty of meaning, arbitrarily long, and contain characters outside the key grammar. Match it with `equals`
  or a `switch` expression over known constants; never use it to index an array without a bounds check, and
  never build a regular expression out of it.

### Unknown path versus known key with no value

This distinction is the contract your consumers depend on, and only your resolvers can maintain it.

| Resolver returns          | PlaceholderAPI shows                    | Means                                                       |
|---------------------------|-----------------------------------------|-------------------------------------------------------------|
| `null`                    | the literal `%identifier_path%`          | there is no such placeholder                                 |
| `PlaceholderValues.UNAVAILABLE` (`---`) | `---`                     | the placeholder exists; the value is not available right now |
| any other string          | that string                              | the value                                                    |

An unresolved literal is loud on purpose: an administrator who mistypes a key sees the mistake on their
scoreboard instead of a blank. `---` is quiet on purpose: the key is right and the data is simply not there
yet — the plugin is still starting, the player has no claim, the peer is offline.

**A resolver that returns `null` for a key it owns is indistinguishable from a typo**, and there is no
diagnostic that will tell your users otherwise. Return `UNAVAILABLE` for missing data. Reserve `null` for
paths you genuinely do not recognise.

### Published keys

`keys()` returns an immutable, ASCII-sorted list of every declared exact key plus one `<group>.*` entry per
group. It is what `getPlaceholders()` hands to PlaceholderAPI for its expansion listing. Because `*` sorts
before letters, a group appears immediately before the exact keys that share its prefix:

```
[available, skill.*, skill.count]
```

There is no way to enumerate the concrete paths behind a group — the point of a group is that its space is
open — so document those paths yourself.

`containsKey(String)` tests **exact keys only**. With only `group("skill")` declared, `containsKey("skill")`
is `false`. It is the check the base class uses to enforce the reserved `available` key, which therefore has
to be a real exact key and cannot be satisfied by a group.

---

## The snapshot holder and the per-player store

These are the required threading pattern, not a convenience. The owning thread publishes; the resolver reads.

### `PlaceholderSnapshot<T>` — one value for the whole server

```java
public T get()
public boolean isPresent()
public String available()
public long publishedAtMs()
public void publish(T snapshot)
```

A `volatile` reference and a `volatile` timestamp. A fresh instance reads as `null` / `false` / `"false"` /
`0`. `publish(null)` clears the value back to unavailable and is what you call on disable.

`available()` is `PlaceholderValues.bool(isPresent())`, which is why the reserved `available` key is usually
just a method reference to it.

Two limitations, stated so you do not find them by debugging:

- `publishedAtMs` is written **before** the value. A reader can observe the new timestamp one instruction
  ahead of the new value. Decide freshness from the value, never from the stamp.
- There is no atomicity **between** holders. A string containing both a server-scoped and a player-scoped
  placeholder can mix data from two different publish rounds. If two numbers must agree with each other, put
  them in the same snapshot record.

### `PlayerSnapshotStore<T>` — one value per player

```java
public static final long DEFAULT_GRACE_MS = 60_000L;

public void publish(UUID playerId, T snapshot)
public T get(UUID playerId)
public boolean isPresent(UUID playerId)
public String available(UUID playerId)
public void evictAfterGrace(UUID playerId, long graceMs)
public void clear()
```

A `ConcurrentHashMap` keyed by player id, with a lazily-evaluated expiry per entry.

| Call                              | Behaviour                                                                    |
|-----------------------------------|-------------------------------------------------------------------------------|
| `publish(id, snapshot)`           | Replaces the entry and clears any pending expiry                              |
| `publish(id, null)`               | Removes the entry outright                                                    |
| `publish(null, snapshot)`         | No-op                                                                         |
| `get(null)`                       | `null`                                                                        |
| `get(id)` past its expiry         | Removes the entry and returns `null`                                          |
| `evictAfterGrace(id, graceMs > 0)` | Stamps the existing entry to expire `graceMs` from now. No entry, no effect   |
| `evictAfterGrace(id, 0)` or negative | Removes the entry immediately                                              |
| `available(id)`                    | `"true"` or `"false"`, honouring expiry                                       |
| `clear()`                          | Empties the map                                                               |

`DEFAULT_GRACE_MS` is one minute. The grace window exists because a placeholder is often read slightly after
the event that ends its validity — a quit message, a death screen, a scoreboard tick already in flight. A
player who reconnects inside the window gets a fresh `publish` that cancels the pending expiry, so the grace
never fights a rejoin.

Expiry is evaluated **on read**. An entry whose window has passed but whose key nobody looks up again stays in
the map. The map is therefore bounded by the number of distinct players seen since the last `clear()`, not by
the number online. Call `clear()` on disable, as the worked example does. If you want an entry gone
immediately and unconditionally, `evictAfterGrace(id, 0L)`.

---

## Value formatter reference

`PlaceholderValues` is a static utility with a private constructor. Every method is pure and safe from any
thread.

| Call                    | Produces                                                                                     |
|-------------------------|----------------------------------------------------------------------------------------------|
| `bool(true)`            | `true`                                                                                        |
| `bool(false)`           | `false`                                                                                       |
| `count(412L)`           | `412`                                                                                         |
| `count(-1234567L)`      | `-1234567`                                                                                    |
| `count(Long.MAX_VALUE)` | `9223372036854775807`                                                                         |
| `num(0.0D)`             | `0.00`                                                                                        |
| `num(19.99D)`           | `19.99`                                                                                       |
| `num(12.333333333D)`    | `12.33`                                                                                       |
| `num(1.995D)`           | `2.00`                                                                                        |
| `num(-4.5D)`            | `-4.50`                                                                                       |
| `num(-0.001D)`          | `0.00`                                                                                        |
| `num(1234567.891D)`     | `1234567.89`                                                                                  |
| `num(Double.NaN)`       | `---`                                                                                         |
| `percent(0.532D)`       | `53.20`                                                                                       |
| `percent(1.0D)`         | `100.00`                                                                                      |
| `text("Riverbend")`     | `Riverbend`                                                                                   |
| `text("§aMining")` | `Mining`                                                                                      |
| `text("100% done")`     | `100 done`                                                                                    |
| `text(null)`            | `---`                                                                                         |

### The rules behind that table

**No grouping separator, ever.** `count` and `num` build their output digit by digit rather than going through
`String.format` or `DecimalFormat`, so the result never depends on the JVM's default locale. A server started
with a comma-decimal locale still emits `1234567.89`, and a consumer parsing your value with
`Double.parseDouble` never breaks.

**No percent character, ever.** `percent` multiplies by 100 and formats the number — it does not append `%`.
`text` deletes every `%` it finds. A `%` in a value re-enters PlaceholderAPI's replacer on the next pass and
can be re-parsed as a placeholder of its own; stripping it is the only reliable defence. If you want a percent
sign displayed, put the literal in the template beside the placeholder, not in the value.

**`num` is fixed at two decimals**, rounding half-up on the magnitude — `1.995` becomes `2.00` and `-4.5`
becomes `-4.50`. A negative value that rounds to zero drops its sign, so `-0.001` is `0.00` and never
`-0.00`. Magnitudes at or above `9.0e16` exceed what the fixed-point scaling can hold; they render as the
whole number followed by `.00`, losing any fractional part.

**`count` is allocation-free for small values.** Values from `0` to `1023` return a cached instance. Counts,
levels and sizes are the common case on a hot read path, and they cost nothing.

**`text` strips legacy formatting.** A section character and the character following it are both removed, so
`§aMining` becomes `Mining`. A trailing section character with nothing after it is dropped on its own.
When nothing needs stripping, the original instance is returned unchanged.

**Unavailable is `---`.** `UNAVAILABLE` is what `num` returns for `NaN` and both infinities, what `text`
returns for `null`, for the empty string, and for a value that was nothing but formatting codes, and what your
resolvers should return whenever a known key has no value. Consumers can test a placeholder against `---` to
decide whether to render a whole line.

### Constants

There are no enums in this package, so nothing here can break your `switch` by gaining a constant later.

| Constant                              | Value      | Meaning                                                     |
|---------------------------------------|------------|--------------------------------------------------------------|
| `PlaceholderKeyRegistry.AVAILABLE`    | `available` | The reserved key every expansion must publish                |
| `PlaceholderValues.UNAVAILABLE`       | `---`      | Known key, no value right now                                |
| `PlaceholderValues.TRUE`              | `true`     | Boolean true                                                 |
| `PlaceholderValues.FALSE`             | `false`    | Boolean false                                                |
| `PlayerSnapshotStore.DEFAULT_GRACE_MS` | `60000`    | Default eviction grace, in milliseconds                     |

`available` is mandatory. The constructor throws `IllegalArgumentException` if the registry does not declare
it, so every expansion built on this base answers one question the same way: is this plugin's data usable at
all right now. Consumers gate on it before rendering anything else.

---

## Expansion and registration reference

```java
protected VolmitPlaceholderExpansion(
    String identifier,
    String author,
    String version,
    String requiredPlugin,
    PlaceholderKeyRegistry registry,
    Logger logger)
```

Six arguments, all required, in that order. `identifier` must match `[a-z0-9]` and `registry` must declare
`available`; the other four are null-checked and otherwise passed straight through to PlaceholderAPI.

`requiredPlugin` is not a label. PlaceholderAPI listens for `PluginDisableEvent` and unregisters **every**
expansion whose `getRequiredPlugin()` matches the disabling plugin's name, case-insensitively, however that
expansion was registered. Pass your own plugin's name and the expansion goes away with your plugin, which is
what you want. Pass somebody else's and your placeholders vanish when they reload; pass `"PlaceholderAPI"` and
you have written a name that never matches, because PlaceholderAPI skips its own disable event.

```java
public PlaceholderRegistration(Logger logger)

public static boolean isPlaceholderApiEnabled()

public boolean register(Supplier<VolmitPlaceholderExpansion> factory)
public void unregister()
public boolean isRegistered()
```

`PlaceholderRegistration` holds at most one expansion, and it is the only place in this package that calls
`PlaceholderExpansion.register()`. The factory is a `Supplier` rather than an instance so that nothing is
constructed on a server without PlaceholderAPI, and so that a late `PluginEnableEvent` can build the
expansion at the moment it becomes registerable.

`isRegistered()` reports whether an expansion is currently **held**, which is not the same question as whether
PlaceholderAPI still has it. It is a plain read of the `volatile` field and is safe from any thread, but it is
a snapshot and not a lock: on the main thread, where `register` and `unregister` are required to run, it is
exactly the state you would expect, and anywhere else it can be stale by the time you act on it. Use it for
diagnostics and for status output; do not use it to decide whether to call `register`, which already makes
that check itself and returns `true` when an expansion is held.

The two can genuinely disagree. PlaceholderAPI unregisters your expansion by itself when the plugin named by
`requiredPlugin` disables, and `PlaceholderRegistration` is never told; it goes on reporting `true` and
`register()` goes on short-circuiting. In practice the plugin named is your own, so both facts stop mattering
in the same instant. If you make your expansion depend on somebody else's plugin, you own the re-registration
after they come back, and a `PluginEnableEvent` handler alone will not do it — you have to `unregister()`
first so the held reference is dropped.

---

## Failure policy

The base class assumes your resolvers will throw, that PlaceholderAPI will be absent, that it will arrive
late, and that it will refuse a registration.

| Situation                                              | What happens                                                                                              |
|--------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| A resolver throws any `Throwable`, including an `Error` | `onRequest` returns `---`. One `WARNING` with the stack trace per distinct path, capped at 64 distinct paths |
| A resolver keeps throwing                              | Called again every time. No quarantine, no fault counter, no disable threshold                              |
| A resolver returns `null`                              | Treated as unknown. PlaceholderAPI leaves the literal `%identifier_path%` in the string                     |
| The `OfflinePlayer` argument throws from `getUniqueId()` | Caught by the same barrier. Returns `---`                                                                 |
| `params` is `null`, empty or blank                     | Returns `null` before any lookup. Nothing is logged and no resolver runs                                   |
| The logger itself throws while reporting a failure     | Swallowed. The `---` is still returned                                                                     |
| The factory passed to `register` throws                | `SEVERE` with the stack trace. `register` returns `false`                                                  |
| The factory returns `null`                             | `register` returns `false`. Nothing is logged                                                              |
| PlaceholderAPI refuses the registration                | `WARNING` naming the identifier. `register` returns `false`                                                |
| PlaceholderAPI throws during registration              | `SEVERE` with the stack trace. `register` returns `false`                                                  |
| PlaceholderAPI is not enabled                          | `register` returns `false` silently. Nothing is retried automatically                                      |
| `register` called while already registered             | Returns `true` immediately. No second expansion is built or registered                                     |
| The plugin named by `requiredPlugin` disables          | PlaceholderAPI unregisters the expansion itself and logs it. `PlaceholderRegistration` is not told and still reports `isRegistered()` as `true` |
| `unregister` when nothing is registered                | No-op                                                                                                       |
| `unregister` after PlaceholderAPI already dropped it   | No-op. The second unregistration is refused quietly and nothing is logged                                   |
| PlaceholderAPI throws during unregistration            | `WARNING`. The held reference was already cleared, so a later `register` can succeed                        |
| A bad identifier, or a registry without `available`    | `IllegalArgumentException` from the constructor — inside your factory, so it surfaces as the `SEVERE` above |

`PlaceholderRegistration.isPlaceholderApiEnabled()` is a static probe you can call before doing any setup
work; it returns `false` rather than throwing if the plugin manager is not available yet.

The whole path is total by design. `onRequest` cannot throw, `register` cannot throw, `unregister` cannot
throw. The one place an exception escapes is the `VolmitPlaceholderExpansion` constructor, and it escapes on
purpose — a malformed identifier or a missing reserved key is a bug in your plugin that should be visible at
startup, not a placeholder that quietly never resolves.

---

## Resolving placeholders in your own text

`art.arcane.volmlib.util.bukkit.Placeholders` is the consumer side: text your plugin is about to display that
may contain `%...%` written by an administrator.

```java
public static boolean containsPlaceholder(String text)
public static String setPlaceholders(Player player, String text)
```

```java
package com.example.tiny;

import art.arcane.volmlib.util.bukkit.Placeholders;
import org.bukkit.entity.Player;

public final class TinyMotd {
    private TinyMotd() {
    }

    public static String render(Player player, String template) {
        if (!Placeholders.containsPlaceholder(template)) {
            return template;
        }

        return Placeholders.setPlaceholders(player, template);
    }
}
```

`setPlaceholders` never throws and never returns `null` for a non-null input:

| Situation                                       | Result                                                             |
|-------------------------------------------------|---------------------------------------------------------------------|
| `player` is `null`, or the text has no `%`      | The text, untouched. No reflection, no lookup                       |
| PlaceholderAPI is not installed or not enabled  | The text, untouched                                                 |
| PlaceholderAPI is present but the method is gone | The text, untouched. One `WARNING`, logged once and never repeated  |
| A third-party expansion throws during resolution | The **original** text, not a partially-resolved one. One `WARNING`, logged once and never repeated |
| Resolution succeeds                             | The resolved text                                                   |

It reaches PlaceholderAPI reflectively rather than linking against it, so your plugin does not need
PlaceholderAPI on its compile classpath to call this, and does not fail to load without it. The resolved
method is cached and re-probed at most once per second, so a PlaceholderAPI installed after your plugin
started is picked up within a second, and one disabled at runtime stops being called within a second — no
restart, and no class lookup per call.

Its own work is thread-agnostic, but **what it invokes is not**. It runs whichever third-party expansions the
text names, and those have no obligation to be safe off the main thread. On Folia, call it from the thread
that owns the player you are passing — the player's entity scheduler, or the region thread you are already on.
Everything under `art.arcane.volmlib.util.bukkit.papi` is safe there; nobody else's expansion has promised
anything.

Finally, note the static state: the cached method handle, the probe deadline and the "already logged" flags
are per copy of the class. A plugin that relocates VolmLib has its own cache and its own one-time warnings,
independent of every other plugin on the server. That is by design — see
[README.md](README.md#the-relocation-rule).
