# VolmLib

VolmLib is the code the Volmit plugins are built out of: Folia-aware scheduling, the Director command
framework, localization, PlaceholderAPI plumbing, NBT and region-file IO, chunk-shaped storage, and the small
Bukkit helpers that every one of those plugins would otherwise write twice. It is a **library, not a plugin**.
The published artifact contains no `plugin.yml` and no resources at all — only classes under
`art.arcane.volmlib`. Nothing loads it on the server; it is compiled and shaded into your jar. That single
fact drives the rest of this page.

| Document                             | Covers                                                              |
|--------------------------------------|---------------------------------------------------------------------|
| This file                            | Depending on VolmLib, and the relocation rule                        |
| [placeholders.md](placeholders.md)   | `art.arcane.volmlib.util.bukkit.papi` and `Placeholders`             |

---

## What is in it

| Package                              | What lives there                                                                                   |
|--------------------------------------|-----------------------------------------------------------------------------------------------------|
| `util.scheduling`                    | `FoliaScheduler` (global/region/entity dispatch that works on Paper and Folia alike), `SchedulerRuntime`, `Looper`, `ChronoLatch`, `PrecisionStopwatch`, `IrisLock` |
| `util.bukkit`                        | `Events`, `WorldIdentity`, `ChunkPositionSet`, `Placeholders`                                       |
| `util.bukkit.papi`                   | The shared PlaceholderAPI expansion base and its key registry — see [placeholders.md](placeholders.md) |
| `util.director`                      | The Director command framework: annotations, parameter handlers, help rendering, tab completion      |
| `util.localization`                  | `LocalizationManager`, `MessageCatalog`, locale overlays, plural selection, catalogue validation      |
| `util.format`                        | `Form` (durations, memory sizes, wrapping, capitalisation), `ColorFormatter`, `MemoryMonitor`         |
| `util.board`                         | Scoreboard sidebar management (`BoardManager`, `BoardProvider`, `BoardSettings`)                      |
| `util.inventorygui`                  | Chest-menu building                                                                                  |
| `util.nbt`, `util.nbt.mca`           | NBT tags, and reading and writing Anvil region files                                                 |
| `util.hunk`, `util.matter`, `util.mantle` | Three-dimensional chunk-shaped buffers, palette-backed storage, and the persistent world-data layer |
| `util.noise`, `util.interpolation`, `util.stream` | Noise generators, interpolators, and composable procedural streams                        |
| `util.collection`, `util.cache`, `util.data` | `KList`/`KMap`/`KSet`, chunk caches, palettes, cuboids, varint helpers                        |
| `util.io`, `util.json`             | IO adapters and the JSON implementation                                                              |
| `util.network`                     | Downloads, metered streams, download progress reporting                                              |
| `integration`                        | The cross-plugin metric handshake types. Read the relocation rule below before you touch these        |

Everything else under `art.arcane.volmlib` is scaffolding for the Volmit plugins. It is public because Java
has no better word for "visible to the plugins in this repository", not because it carries a compatibility
promise. Only the surfaces documented in this directory are stable across VolmLib versions; anything else can
change signature without notice, and the Volmit plugins update in lockstep because they build against the
same source tree.

---

## Depending on VolmLib

VolmLib is published to JitPack and shaded into the consuming plugin.

```groovy
repositories {
    mavenCentral()
    maven {
        url = uri('https://repo.papermc.io/repository/maven-public/')
    }
    maven {
        url = uri('https://jitpack.io')
    }
}

dependencies {
    implementation('com.github.VolmitSoftware:VolmLib:master-SNAPSHOT') {
        changing = true
        transitive = false
    }
}
```

Three details in that block are load-bearing:

- **`implementation`, not `compileOnly`.** Nothing on the server provides these classes. If you do not shade
  them you get `NoClassDefFoundError: art/arcane/volmlib/...` the first time your code touches VolmLib.
- **`transitive = false`.** VolmLib compiles against Gson, Guava, fastutil, Caffeine, commons-lang3,
  concurrentlinkedhashmap-lru and lz4-java. Pulling those in transitively drags a second copy of libraries the
  server or your other dependencies already ship. Add back by hand only what you actually call.
- **`changing = true`** on a `-SNAPSHOT` coordinate, so Gradle re-resolves instead of serving a week-old jar.
  Pin a commit hash (`com.github.VolmitSoftware:VolmLib:<sha>`) for reproducible builds.

VolmLib targets a **Java 25 toolchain** and compiles against `io.papermc.paper:paper-api`. It also compiles
cleanly against `org.spigotmc:spigot-api`, so a Spigot-only plugin can use it; anything that needs Paper types
is confined to the packages that name them.

### Building against a local checkout

Every Volmit plugin resolves VolmLib from a sibling directory when one is present, via a Gradle composite
build. The consumer's `settings.gradle` does the substitution:

```groovy
includeBuild('../VolmLib') {
    dependencySubstitution {
        substitute(module('com.github.VolmitSoftware:VolmLib')).using(project(':shared'))
        substitute(module('com.github.VolmitSoftware.VolmLib:shared')).using(project(':shared'))
        substitute(module('com.github.VolmitSoftware.VolmLib:volmlib-shared')).using(project(':shared'))
    }
}
```

All three spellings need substituting, because all three can appear in a resolved graph: the whole-repository
JitPack form (`com.github.VolmitSoftware:VolmLib`), the subproject form built from the Gradle project name
(`com.github.VolmitSoftware.VolmLib:shared`), and the subproject form built from the Maven publication's
`artifactId`, which is `volmlib-shared` (`com.github.VolmitSoftware.VolmLib:volmlib-shared`). Substituting
only some of them leaves you compiling against a local checkout while resolving something else at runtime,
which is worse than substituting none.

---

## The relocation rule

This is the most important thing on this page.

VolmLib classes live inside your jar. Some plugins shade them under `art.arcane.volmlib` unchanged; others
rewrite the package during shading so their copy cannot collide with anyone else's. In this suite, both shapes
are in production at once:

| Plugin     | Package its copy of VolmLib ends up in           |
|------------|--------------------------------------------------|
| Iris       | `art.arcane.volmlib`                             |
| Wormholes  | `art.arcane.volmlib`                             |
| HoloUi     | `art.arcane.volmlib`                             |
| HiddenOre  | `art.arcane.volmlib`                             |
| BileTools  | `art.arcane.volmlib`                             |
| Adapt      | `art.arcane.adapt.util.arcane.volmlib`           |
| React      | `art.arcane.react.util.arcane.volmlib`           |

`art.arcane.volmlib.util.bukkit.papi.PlaceholderValues` and
`art.arcane.adapt.util.arcane.volmlib.util.bukkit.papi.PlaceholderValues` are different classes, in different
classloaders, with separate static state. They are not assignable to one another and never will be. Which
brings us to the rule.

> **A VolmLib type is safe only if every reference to it stays inside one plugin's own jar, or travels through
> a third-party type that is not relocated.**

Nothing else is safe. Not "usually works". Not "works if both plugins are on the same version". If a class
name written by plugin A has to be resolved by plugin B, and either plugin rewrites that name at build time,
the link fails.

### Why the placeholder base class is safe

`VolmitPlaceholderExpansion` is a VolmLib type that PlaceholderAPI — a completely separate plugin — calls
into. It satisfies the rule because of what sits at the boundary:

```
your jar                                  PlaceholderAPI's jar
--------                                  --------------------
YourExpansion
  extends VolmitPlaceholderExpansion
    extends PlaceholderExpansion  ------>  me.clip.placeholderapi.expansion.PlaceholderExpansion
      onRequest(OfflinePlayer, String) : String
```

The only name crossing the jar boundary is `PlaceholderExpansion`, which belongs to PlaceholderAPI and is
never relocated by anybody — it is declared with a compile-only scope in every consuming build (`compileOnly`,
or `compileOnlyApi` where the type appears in that plugin's own API), so it is never shaded, and it is
provided at runtime by the PlaceholderAPI plugin itself. Everything on your side of that arrow is your own
copy: your subclass, the base class, the registry, the value formatter. PlaceholderAPI stores your object as a
`PlaceholderExpansion`, calls `onRequest`, and gets back a `java.lang.String`. Every type in the signature is
a PlaceholderAPI type, a Bukkit type or a JDK type.

That is why Adapt and Wormholes can both register a placeholder expansion built on the same base class on the
same server, with the class relocated in one jar and not the other, and neither notices the other exists.

### What is not safe

| Pattern                                                                     | What goes wrong                                                                                     |
|-----------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| Registering a VolmLib type with `ServicesManager` for another plugin to load | Provider and consumer look up different `Class` objects. `load()` returns `null`, silently           |
| A Bukkit event with a getter that returns a VolmLib type                     | The listener cannot name the return type without a `NoClassDefFoundError`                            |
| A public API method on your plugin taking or returning `KList`, `KMap`, `Cuboid`, or any other VolmLib type | Any caller that relocates differently gets `NoClassDefFoundError` at the call site   |
| `instanceof` or a cast against a VolmLib type on an object from another plugin | The cast never matches. On a shared supertype you get `ClassCastException`                         |
| Assuming static state in VolmLib is server-global                            | Each relocated copy has its own statics. A cache primed in Adapt's copy is invisible to Iris's copy   |
| Serializing a VolmLib type into a PDC value, plugin message or metadata for another plugin to read | The reader cannot resolve the class name that was written                          |

The `art.arcane.volmlib.integration` package is exactly this hazard in the wild. Several plugins register an
`IntegrationServiceContract` implementation with Bukkit's `ServicesManager` under their own copy of that
interface, so a straightforward consumer sees nothing:

```java
import art.arcane.volmlib.integration.IntegrationServiceContract;
import org.bukkit.Bukkit;

IntegrationServiceContract contract = Bukkit.getServicesManager().load(IntegrationServiceContract.class);
```

That returns `null` for every provider whose jar relocated VolmLib differently from yours, and returns a
provider only by the coincidence that you both chose the same shading. It never throws, so the failure looks
like "the other plugin is not installed".

### Crossing a boundary on purpose

If a value genuinely has to move between plugins, one of these has to be true at the boundary:

1. **The type is a JDK or Bukkit type.** `String`, `UUID`, `Map<String, String>`, `Location`, `ItemStack`.
   This is the right answer almost every time.
2. **The type is a third-party type nobody relocates**, provided at runtime by the plugin that owns it —
   `PlaceholderExpansion` is the worked example above.
3. **The type is declared in your own plugin's package and you never relocate your own package.** Publish it
   from your jar; consumers compile against your jar.
4. **Nothing is named at all** — the consumer works reflectively.

Option 4, applied to the services case, finds every provider regardless of how it was shaded, by matching on
the simple name and calling through `java.lang.reflect`:

```java
package com.example.claims;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ForeignIntegrationLookup {
    private static final String CONTRACT_SIMPLE_NAME = "IntegrationServiceContract";

    private ForeignIntegrationLookup() {
    }

    public static List<Object> providers() {
        ServicesManager services = Bukkit.getServicesManager();
        List<Object> found = new ArrayList<>();

        for (Class<?> service : services.getKnownServices()) {
            if (!CONTRACT_SIMPLE_NAME.equals(service.getSimpleName())) {
                continue;
            }

            for (RegisteredServiceProvider<?> registration : services.getRegistrations(service)) {
                found.add(registration.getProvider());
            }
        }

        return found;
    }

    public static String pluginIdOf(Object provider) {
        try {
            Method pluginId = provider.getClass().getMethod("pluginId");
            Object value = pluginId.invoke(provider);
            return value instanceof String text ? text : null;
        } catch (ReflectiveOperationException failure) {
            return null;
        }
    }
}
```

The providers come back as `Object`. Every method you call on them returns a JDK type or has to be unwrapped
reflectively in turn, which is the honest cost of the approach: you trade compile-time checking for the
ability to talk to a jar that renamed its classes. Call `providers()` on the main (global region) thread —
`ServicesManager` is not documented as thread-safe, and the providers you get back may not be either.

### Recognising the failure

| Symptom                                                                   | Cause                                                                    |
|---------------------------------------------------------------------------|--------------------------------------------------------------------------|
| `NoClassDefFoundError: art/arcane/volmlib/...` at your own call site       | You depended on VolmLib with `compileOnly`, or your shade step dropped it |
| `NoClassDefFoundError: art/arcane/volmlib/...` inside another plugin's stack | You exposed a VolmLib type in your public API                          |
| `ServicesManager.load(...)` returns `null` with the provider clearly enabled | Provider and consumer relocated VolmLib differently                    |
| `ClassCastException` between two identically-named classes                 | Two copies of the same VolmLib type, one relocated                       |
| State written through VolmLib in one plugin is invisible in another        | Per-copy statics, exactly as designed                                    |

---

## Threading

Everything in this suite runs on Folia as well as Paper, so "the main thread" is not a place. Region threads
own worlds and chunks, entity schedulers own entities, and the global region thread owns everything that
belongs to no region.

VolmLib does not hide this. `art.arcane.volmlib.util.scheduling.FoliaScheduler` exposes the dispatch you need
and behaves correctly on both server types:

| Method                                                           | Runs the task where                                             |
|------------------------------------------------------------------|-----------------------------------------------------------------|
| `runGlobal(Plugin, Runnable)` / `runGlobal(Plugin, Runnable, long)` | Global region thread on Folia, main thread on Paper           |
| `runRegion(Plugin, Location, Runnable[, long])`                   | The region owning that location                                 |
| `runRegion(Plugin, World, int, int, Runnable[, long])`            | The region owning that chunk                                    |
| `runEntity(Plugin, Entity, Runnable[, long])`                     | The entity's scheduler; runs inline when you already own the entity and asked for no delay |
| `runEntity(Plugin, Entity, Runnable, long, Runnable)`             | The same, with a retired callback run instead when the entity is gone |

Each returns `boolean`: `true` when the task was scheduled or run, `false` when it could not be (a disabled
plugin, a null argument, a location with no world, a retired entity). A `false` return is not an exception and
does not retry — check it if the work matters. On the five-argument `runEntity` the retired callback runs
whenever the entity turns out to be gone, on both sides of that boolean: `true` when the call was already on
the entity's own thread and could see the entity was retired, `false` when the entity scheduler rejected the
task. Do the cleanup in the callback, not in the `false` branch.

`FoliaScheduler.isFolia(Server)`, `isFoliaThreading(Server)`, `isPrimaryThread()` and the
`isOwnedByCurrentRegion(...)` overloads answer "may I touch this from here" without scheduling anything.

Everything else in VolmLib states its own threading contract, or has none and must be treated as
single-threaded. The one surface documented in this directory that is explicitly safe from any thread is the
placeholder snapshot machinery, and [placeholders.md](placeholders.md) explains exactly why it has to be.
