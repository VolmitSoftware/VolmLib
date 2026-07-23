package art.arcane.volmlib.integration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class IntegrationMetricSchema {
    public static final String IRIS_WORLD_COUNT = "iris.world-count";
    public static final String IRIS_ENGINE_ACTIVE = "iris.engine-active";
    public static final String IRIS_ENGINE_CLOSING = "iris.engine-closing";
    public static final String IRIS_ENGINE_FAILED = "iris.engine-failed";
    public static final String IRIS_ENGINE_STUDIO = "iris.engine-studio";
    public static final String IRIS_ENGINE_PENDING_REGISTRATIONS = "iris.engine-pending-registrations";
    public static final String IRIS_LOADED_CHUNKS = "iris.loaded-chunks";
    public static final String IRIS_LOADED_ENTITIES = "iris.loaded-entities";
    public static final String IRIS_ENTITY_SATURATION = "iris.entity-saturation";
    public static final String IRIS_CHUNKS_GENERATED_SESSION = "iris.chunks-generated-session";
    public static final String IRIS_CHUNKS_GENERATED_TOTAL = "iris.chunks-generated-total";
    public static final String IRIS_CHUNKS_PER_SECOND = "iris.chunks-per-second";
    public static final String IRIS_BLOCK_UPDATES_PER_SECOND = "iris.block-updates-per-second";
    public static final String IRIS_ENGINE_PARALLELISM = "iris.engine-parallelism";
    public static final String IRIS_GENERATION_ACTIVE_LEASES = "iris.generation-active-leases";
    public static final String IRIS_HOTLOADS_TOTAL = "iris.hotloads-total";
    public static final String IRIS_MAINTENANCE_ACTIVE_TASKS = "iris.maintenance-active-tasks";
    public static final String IRIS_MAINTENANCE_WORKERS = "iris.maintenance-workers";
    public static final String IRIS_MANTLE_RESIDENT_PLATES = "iris.mantle-resident-plates";
    public static final String IRIS_MANTLE_QUEUED_PLATES = "iris.mantle-queued-plates";
    public static final String IRIS_MANTLE_IDLE_AVERAGE_MS = "iris.mantle-idle-average-ms";
    public static final String IRIS_MANTLE_IDLE_MAX_MS = "iris.mantle-idle-max-ms";
    public static final String IRIS_MANTLE_IDLE_MIN_MS = "iris.mantle-idle-min-ms";
    public static final String IRIS_MANTLE_HEAP_USAGE = "iris.mantle-heap-usage";
    public static final String IRIS_MANTLE_RECLAIM_URGENCY = "iris.mantle-reclaim-urgency";
    public static final String IRIS_CACHE_COUNT = "iris.cache-count";
    public static final String IRIS_CACHE_ENTRIES = "iris.cache-entries";
    public static final String IRIS_CACHE_CAPACITY = "iris.cache-capacity";
    public static final String IRIS_CACHE_USAGE = "iris.cache-usage";
    public static final String IRIS_CACHE_RESOURCE_COUNT = "iris.cache-resource-count";
    public static final String IRIS_CACHE_RESOURCE_ENTRIES = "iris.cache-resource-entries";
    public static final String IRIS_CACHE_RESOURCE_CAPACITY = "iris.cache-resource-capacity";
    public static final String IRIS_CACHE_RESOURCE_USAGE = "iris.cache-resource-usage";
    public static final String IRIS_CACHE_STREAM_2D_COUNT = "iris.cache-stream-2d-count";
    public static final String IRIS_CACHE_STREAM_2D_ENTRIES = "iris.cache-stream-2d-entries";
    public static final String IRIS_CACHE_STREAM_2D_CAPACITY = "iris.cache-stream-2d-capacity";
    public static final String IRIS_CACHE_STREAM_2D_USAGE = "iris.cache-stream-2d-usage";
    public static final String IRIS_CACHE_STREAM_3D_COUNT = "iris.cache-stream-3d-count";
    public static final String IRIS_CACHE_STREAM_3D_ENTRIES = "iris.cache-stream-3d-entries";
    public static final String IRIS_CACHE_STREAM_3D_CAPACITY = "iris.cache-stream-3d-capacity";
    public static final String IRIS_CACHE_STREAM_3D_USAGE = "iris.cache-stream-3d-usage";
    public static final String IRIS_CACHE_OTHER_COUNT = "iris.cache-other-count";
    public static final String IRIS_CACHE_OTHER_ENTRIES = "iris.cache-other-entries";
    public static final String IRIS_CACHE_OTHER_CAPACITY = "iris.cache-other-capacity";
    public static final String IRIS_CACHE_OTHER_USAGE = "iris.cache-other-usage";
    public static final String IRIS_PREGEN_ACTIVE = "iris.pregen-active";
    public static final String IRIS_PREGEN_PAUSED = "iris.pregen-paused";
    public static final String IRIS_PREGEN_PROGRESS = "iris.pregen-progress";
    public static final String IRIS_PREGEN_GENERATED = "iris.pregen-generated";
    public static final String IRIS_PREGEN_TOTAL = "iris.pregen-total";
    public static final String IRIS_PREGEN_QUEUE = "iris.pregen-queue";
    public static final String IRIS_PREGEN_THROUGHPUT = "iris.pregen-throughput";
    public static final String IRIS_PREGEN_ETA_MS = "iris.pregen-eta-ms";
    public static final String IRIS_PREGEN_ELAPSED_MS = "iris.pregen-elapsed-ms";
    public static final String IRIS_PREGEN_FAILED = "iris.pregen-failed";
    public static final String IRIS_GENERATION_TOTAL_MS = "iris.generation-total-ms";
    public static final String IRIS_GENERATION_UPDATES_MS = "iris.generation-updates-ms";
    public static final String IRIS_GENERATION_TERRAIN_MS = "iris.generation-terrain-ms";
    public static final String IRIS_GENERATION_BIOME_MS = "iris.generation-biome-ms";
    public static final String IRIS_GENERATION_POST_MS = "iris.generation-post-ms";
    public static final String IRIS_GENERATION_PERFECTION_MS = "iris.generation-perfection-ms";
    public static final String IRIS_GENERATION_DECORATION_MS = "iris.generation-decoration-ms";
    public static final String IRIS_GENERATION_CAVE_MS = "iris.generation-cave-ms";
    public static final String IRIS_GENERATION_DEPOSIT_MS = "iris.generation-deposit-ms";
    public static final String IRIS_GENERATION_CARVE_RESOLVE_MS = "iris.generation-carve-resolve-ms";
    public static final String IRIS_GENERATION_CARVE_APPLY_MS = "iris.generation-carve-apply-ms";
    public static final String IRIS_GENERATION_CONTEXT_PREFILL_MS = "iris.generation-context-prefill-ms";
    public static final String IRIS_PREGEN_WAIT_PERMIT_MS = "iris.pregen-wait-permit-ms";
    public static final String IRIS_PREGEN_WAIT_ADAPTIVE_MS = "iris.pregen-wait-adaptive-ms";

    public static final String ADAPT_SESSION_LOAD = "adapt.session-load";
    public static final String ADAPT_ABILITY_OPS = "adapt.ability-ops";
    public static final String ADAPT_ABILITY_CHECK_OPS = "adapt.ability-check-ops";
    public static final String ADAPT_ABILITY_CHECK_OPS_TICK = "adapt.ability-check-ops-tick";
    public static final String ADAPT_WORLD_POLICY_LATENCY = "adapt.world-policy-latency";
    public static final String ADAPT_ABILITY_DETAIL_PREFIX = "adapt.ability-detail.";
    public static final String ADAPT_ABILITY_DETAIL_EXECUTION_OPS = "execution-ops";
    public static final String ADAPT_ABILITY_DETAIL_EXECUTION_TIMING_MS = "execution-timing-ms";
    public static final String ADAPT_ABILITY_DETAIL_GUARD_CHECKS = "guard-checks";
    public static final String ADAPT_ABILITY_DETAIL_GUARD_TIMING_MS = "guard-timing-ms";
    public static final String ADAPT_ABILITY_CACHE_HIT_RATIO = "adapt.ability-cache-hit-ratio";
    public static final String ADAPT_ABILITY_CHECK_LATENCY_US = "adapt.ability-check-latency-us";
    public static final String ADAPT_ABILITY_TIMING_BUDGET = "adapt.ability-timing-budget";
    public static final String ADAPT_PLAYER_SESSIONS = "adapt.player-sessions";
    public static final String ADAPT_LEARNED_ADAPTATIONS_ONLINE = "adapt.learned-adaptations-online";
    public static final String ADAPT_SPATIAL_XP_TICKETS = "adapt.spatial-xp-tickets";
    public static final String ADAPT_FX_TIMELINES_ACTIVE = "adapt.fx-timelines-active";
    public static final String ADAPT_FX_PACKETS_USED = "adapt.fx-packets-used";
    public static final String ADAPT_FX_SHED_BAND = "adapt.fx-shed-band";
    public static final String ADAPT_MINIONS_ACTIVE = "adapt.minions-active";
    public static final String ADAPT_PERSISTENCE_QUEUE_DEPTH = "adapt.persistence-queue-depth";
    public static final String ADAPT_XP_PER_MINUTE = "adapt.xp-per-minute";
    public static final String ADAPT_XP_PAYOUT_OPS = "adapt.xp-payout-ops";
    public static final String ADAPT_PROVENANCE_OPS = "adapt.provenance-ops";
    public static final String ADAPT_EVENT_HANDLER_OPS = "adapt.event-handler-ops";

    public static final String WORMHOLES_PORTALS = "wormholes.portals";
    public static final String WORMHOLES_PROJECTIONS_ACTIVE = "wormholes.projections-active";
    public static final String WORMHOLES_PROJECTION_OBSERVERS = "wormholes.projection-observers";
    public static final String WORMHOLES_PROJECTION_RENDER_MS = "wormholes.projection-render-ms";
    public static final String WORMHOLES_BLOCK_CHANGES_PER_SECOND = "wormholes.block-changes-per-second";
    public static final String WORMHOLES_PACKETS_PER_SECOND = "wormholes.packets-per-second";
    public static final String WORMHOLES_SPOOFED_ENTITIES = "wormholes.spoofed-entities";
    public static final String WORMHOLES_TRAVERSALS_PER_MINUTE = "wormholes.traversals-per-minute";
    public static final String WORMHOLES_PEERS_CONNECTED = "wormholes.peers-connected";
    public static final String WORMHOLES_REMOTE_PORTALS = "wormholes.remote-portals";
    public static final String WORMHOLES_PEER_RTT_MAX_MS = "wormholes.peer-rtt-max-ms";
    public static final String WORMHOLES_WIRE_BYTES_OUT_PER_SECOND = "wormholes.wire-bytes-out-per-second";
    public static final String WORMHOLES_WIRE_BYTES_IN_PER_SECOND = "wormholes.wire-bytes-in-per-second";
    public static final String WORMHOLES_COMPRESSION_RATIO_OUT = "wormholes.compression-ratio-out";
    public static final String WORMHOLES_SIDEBAND_QUEUED_BYTES = "wormholes.sideband-queued-bytes";
    public static final String WORMHOLES_SIDEBAND_DROPS_PER_SECOND = "wormholes.sideband-drops-per-second";
    public static final String WORMHOLES_VIEW_SUBSCRIPTIONS = "wormholes.view-subscriptions";
    public static final String WORMHOLES_VIEW_TRACKED_ENTITIES = "wormholes.view-tracked-entities";
    public static final String WORMHOLES_REPLICATED_BLOCKS_PER_SECOND = "wormholes.replicated-blocks-per-second";
    public static final String WORMHOLES_RESYNC_REQUESTS_TOTAL = "wormholes.resync-requests-total";
    public static final String WORMHOLES_TRANSFERS_IN_FLIGHT = "wormholes.transfers-in-flight";
    public static final String WORMHOLES_TRANSFERS_FAILED_TOTAL = "wormholes.transfers-failed-total";

    public static final String HOLOUI_SESSION_HOLDERS = "holoui.session-holders";
    public static final String HOLOUI_MENUS_OPEN = "holoui.menus-open";
    public static final String HOLOUI_PREVIEWS_OPEN = "holoui.previews-open";
    public static final String HOLOUI_DISPLAY_ENTITIES = "holoui.display-entities";
    public static final String HOLOUI_DISPLAY_ENTITIES_VISIBLE = "holoui.display-entities-visible";
    public static final String HOLOUI_MENU_DEFINITIONS = "holoui.menu-definitions";
    public static final String HOLOUI_PACKETS_PER_SECOND = "holoui.packets-per-second";
    public static final String HOLOUI_SPAWNS_PER_SECOND = "holoui.spawns-per-second";
    public static final String HOLOUI_TICK_MS = "holoui.tick-ms";
    public static final String HOLOUI_PREVIEW_REFRESH_PER_SECOND = "holoui.preview-refresh-per-second";
    public static final String HOLOUI_BUILDER_SERVER_RUNNING = "holoui.builder-server-running";

    public static final String HIDDENORE_BLOCKS_BROKEN_PER_SECOND = "hiddenore.blocks-broken-per-second";
    public static final String HIDDENORE_DROPS_INJECTED_PER_SECOND = "hiddenore.drops-injected-per-second";
    public static final String HIDDENORE_VEINS_DISCOVERED_PER_SECOND = "hiddenore.veins-discovered-per-second";
    public static final String HIDDENORE_VEIN_CHUNKS_COMPUTED_PER_SECOND = "hiddenore.vein-chunks-computed-per-second";
    public static final String HIDDENORE_VEIN_CACHE_CHUNKS = "hiddenore.vein-cache-chunks";
    public static final String HIDDENORE_PDC_READS_PER_SECOND = "hiddenore.pdc-reads-per-second";
    public static final String HIDDENORE_PDC_WRITES_PER_SECOND = "hiddenore.pdc-writes-per-second";
    public static final String HIDDENORE_ORE_REMOVAL_ENABLED = "hiddenore.ore-removal-enabled";
    public static final String HIDDENORE_ORE_REMOVAL_BLOCKS_PER_SECOND = "hiddenore.ore-removal-blocks-per-second";
    public static final String HIDDENORE_SEEDED_MODE = "hiddenore.seeded-mode";
    public static final String HIDDENORE_DROP_RULES = "hiddenore.drop-rules";
    public static final String HIDDENORE_CONFIG_RELOADS_TOTAL = "hiddenore.config-reloads-total";

    public static final String BILETOOLS_WATCHED_JARS = "biletools.watched-jars";
    public static final String BILETOOLS_DIRTY_PLUGINS = "biletools.dirty-plugins";
    public static final String BILETOOLS_RELOADS_TOTAL = "biletools.reloads-total";
    public static final String BILETOOLS_LAST_RELOAD_MS = "biletools.last-reload-ms";
    public static final String BILETOOLS_REMOTE_SLAVE_ONLINE = "biletools.remote-slave-online";

    private static final Map<String, IntegrationMetricDescriptor> DESCRIPTORS = buildDescriptors();
    private static final Set<String> IRIS_KEYS = buildIrisKeys();
    private static final Set<String> IRIS_WORLD_KEYS = buildIrisWorldKeys();

    private IntegrationMetricSchema() {
    }

    public static IntegrationMetricDescriptor descriptor(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Metric key cannot be blank");
        }

        IntegrationMetricDescriptor known = DESCRIPTORS.get(key);
        if (known != null) {
            return known;
        }

        AbilityDetailKey abilityDetailKey = parseAbilityDetailKey(key);
        if (abilityDetailKey != null) {
            return abilityDetailDescriptor(key, abilityDetailKey);
        }

        return new IntegrationMetricDescriptor(
                key,
                IntegrationMetricType.DOUBLE,
                "",
                Map.of("origin", "unknown")
        );
    }

    public static Set<IntegrationMetricDescriptor> descriptors() {
        return Set.copyOf(DESCRIPTORS.values());
    }

    public static Set<String> allKeys() {
        return Set.copyOf(DESCRIPTORS.keySet());
    }

    public static Set<String> irisKeys() {
        return IRIS_KEYS;
    }

    public static Set<String> irisWorldKeys() {
        return IRIS_WORLD_KEYS;
    }

    public static Set<String> adaptKeys() {
        return Set.of(
                ADAPT_SESSION_LOAD,
                ADAPT_ABILITY_OPS,
                ADAPT_ABILITY_CHECK_OPS,
                ADAPT_ABILITY_CHECK_OPS_TICK,
                ADAPT_WORLD_POLICY_LATENCY,
                ADAPT_ABILITY_CACHE_HIT_RATIO,
                ADAPT_ABILITY_CHECK_LATENCY_US,
                ADAPT_ABILITY_TIMING_BUDGET,
                ADAPT_PLAYER_SESSIONS,
                ADAPT_LEARNED_ADAPTATIONS_ONLINE,
                ADAPT_SPATIAL_XP_TICKETS,
                ADAPT_FX_TIMELINES_ACTIVE,
                ADAPT_FX_PACKETS_USED,
                ADAPT_FX_SHED_BAND,
                ADAPT_MINIONS_ACTIVE,
                ADAPT_PERSISTENCE_QUEUE_DEPTH,
                ADAPT_XP_PER_MINUTE,
                ADAPT_XP_PAYOUT_OPS,
                ADAPT_PROVENANCE_OPS,
                ADAPT_EVENT_HANDLER_OPS
        );
    }

    public static Set<String> adaptAbilityDetailKeys(String abilityId) {
        String normalizedAbilityId = normalizeAbilityId(abilityId);
        return Set.of(
                ADAPT_ABILITY_DETAIL_PREFIX + normalizedAbilityId + "." + ADAPT_ABILITY_DETAIL_EXECUTION_OPS,
                ADAPT_ABILITY_DETAIL_PREFIX + normalizedAbilityId + "." + ADAPT_ABILITY_DETAIL_EXECUTION_TIMING_MS,
                ADAPT_ABILITY_DETAIL_PREFIX + normalizedAbilityId + "." + ADAPT_ABILITY_DETAIL_GUARD_CHECKS,
                ADAPT_ABILITY_DETAIL_PREFIX + normalizedAbilityId + "." + ADAPT_ABILITY_DETAIL_GUARD_TIMING_MS
        );
    }

    public static boolean isAdaptAbilityDetailKey(String key) {
        return parseAbilityDetailKey(key) != null;
    }

    public static String adaptAbilityId(String key) {
        AbilityDetailKey abilityDetailKey = parseAbilityDetailKey(key);
        return abilityDetailKey == null ? "" : abilityDetailKey.abilityId();
    }

    public static String adaptAbilitySignal(String key) {
        AbilityDetailKey abilityDetailKey = parseAbilityDetailKey(key);
        return abilityDetailKey == null ? "" : abilityDetailKey.signal();
    }

    public static Set<String> wormholesKeys() {
        return Set.of(
                WORMHOLES_PORTALS,
                WORMHOLES_PROJECTIONS_ACTIVE,
                WORMHOLES_PROJECTION_OBSERVERS,
                WORMHOLES_PROJECTION_RENDER_MS,
                WORMHOLES_BLOCK_CHANGES_PER_SECOND,
                WORMHOLES_PACKETS_PER_SECOND,
                WORMHOLES_SPOOFED_ENTITIES,
                WORMHOLES_TRAVERSALS_PER_MINUTE,
                WORMHOLES_PEERS_CONNECTED,
                WORMHOLES_REMOTE_PORTALS,
                WORMHOLES_PEER_RTT_MAX_MS,
                WORMHOLES_WIRE_BYTES_OUT_PER_SECOND,
                WORMHOLES_WIRE_BYTES_IN_PER_SECOND,
                WORMHOLES_COMPRESSION_RATIO_OUT,
                WORMHOLES_SIDEBAND_QUEUED_BYTES,
                WORMHOLES_SIDEBAND_DROPS_PER_SECOND,
                WORMHOLES_VIEW_SUBSCRIPTIONS,
                WORMHOLES_VIEW_TRACKED_ENTITIES,
                WORMHOLES_REPLICATED_BLOCKS_PER_SECOND,
                WORMHOLES_RESYNC_REQUESTS_TOTAL,
                WORMHOLES_TRANSFERS_IN_FLIGHT,
                WORMHOLES_TRANSFERS_FAILED_TOTAL
        );
    }

    public static Set<String> holouiKeys() {
        return Set.of(
                HOLOUI_SESSION_HOLDERS,
                HOLOUI_MENUS_OPEN,
                HOLOUI_PREVIEWS_OPEN,
                HOLOUI_DISPLAY_ENTITIES,
                HOLOUI_DISPLAY_ENTITIES_VISIBLE,
                HOLOUI_MENU_DEFINITIONS,
                HOLOUI_PACKETS_PER_SECOND,
                HOLOUI_SPAWNS_PER_SECOND,
                HOLOUI_TICK_MS,
                HOLOUI_PREVIEW_REFRESH_PER_SECOND,
                HOLOUI_BUILDER_SERVER_RUNNING
        );
    }

    public static Set<String> hiddenoreKeys() {
        return Set.of(
                HIDDENORE_BLOCKS_BROKEN_PER_SECOND,
                HIDDENORE_DROPS_INJECTED_PER_SECOND,
                HIDDENORE_VEINS_DISCOVERED_PER_SECOND,
                HIDDENORE_VEIN_CHUNKS_COMPUTED_PER_SECOND,
                HIDDENORE_VEIN_CACHE_CHUNKS,
                HIDDENORE_PDC_READS_PER_SECOND,
                HIDDENORE_PDC_WRITES_PER_SECOND,
                HIDDENORE_ORE_REMOVAL_ENABLED,
                HIDDENORE_ORE_REMOVAL_BLOCKS_PER_SECOND,
                HIDDENORE_SEEDED_MODE,
                HIDDENORE_DROP_RULES,
                HIDDENORE_CONFIG_RELOADS_TOTAL
        );
    }

    public static Set<String> biletoolsKeys() {
        return Set.of(
                BILETOOLS_WATCHED_JARS,
                BILETOOLS_DIRTY_PLUGINS,
                BILETOOLS_RELOADS_TOTAL,
                BILETOOLS_LAST_RELOAD_MS,
                BILETOOLS_REMOTE_SLAVE_ONLINE
        );
    }

    private static Map<String, IntegrationMetricDescriptor> buildDescriptors() {
        Map<String, IntegrationMetricDescriptor> descriptors = new LinkedHashMap<>();

        addIrisDescriptors(descriptors);

        descriptors.put(ADAPT_SESSION_LOAD, new IntegrationMetricDescriptor(
                ADAPT_SESSION_LOAD,
                IntegrationMetricType.DOUBLE,
                "percent",
                Map.of("plugin", "adapt", "domain", "runtime")
        ));
        descriptors.put(ADAPT_ABILITY_OPS, new IntegrationMetricDescriptor(
                ADAPT_ABILITY_OPS,
                IntegrationMetricType.DOUBLE,
                "ops-per-minute",
                Map.of("plugin", "adapt", "domain", "ability", "signal", "successful-checks")
        ));
        descriptors.put(ADAPT_ABILITY_CHECK_OPS, new IntegrationMetricDescriptor(
                ADAPT_ABILITY_CHECK_OPS,
                IntegrationMetricType.DOUBLE,
                "ops-per-minute",
                Map.of("plugin", "adapt", "domain", "ability", "signal", "all-checks")
        ));
        descriptors.put(ADAPT_ABILITY_CHECK_OPS_TICK, new IntegrationMetricDescriptor(
                ADAPT_ABILITY_CHECK_OPS_TICK,
                IntegrationMetricType.DOUBLE,
                "ops-per-tick",
                Map.of("plugin", "adapt", "domain", "ability", "signal", "all-checks-per-tick")
        ));
        descriptors.put(ADAPT_WORLD_POLICY_LATENCY, new IntegrationMetricDescriptor(
                ADAPT_WORLD_POLICY_LATENCY,
                IntegrationMetricType.DOUBLE,
                "ms",
                Map.of("plugin", "adapt", "domain", "policy")
        ));

        descriptors.put(WORMHOLES_PORTALS, new IntegrationMetricDescriptor(
                WORMHOLES_PORTALS,
                IntegrationMetricType.LONG,
                "portals",
                Map.of("plugin", "wormholes", "domain", "portals")
        ));
        descriptors.put(WORMHOLES_PROJECTIONS_ACTIVE, new IntegrationMetricDescriptor(
                WORMHOLES_PROJECTIONS_ACTIVE,
                IntegrationMetricType.LONG,
                "portals",
                Map.of("plugin", "wormholes", "domain", "projection")
        ));
        descriptors.put(WORMHOLES_PROJECTION_OBSERVERS, new IntegrationMetricDescriptor(
                WORMHOLES_PROJECTION_OBSERVERS,
                IntegrationMetricType.LONG,
                "players",
                Map.of("plugin", "wormholes", "domain", "projection")
        ));
        descriptors.put(WORMHOLES_PROJECTION_RENDER_MS, new IntegrationMetricDescriptor(
                WORMHOLES_PROJECTION_RENDER_MS,
                IntegrationMetricType.DOUBLE,
                "ms-per-second",
                Map.of("plugin", "wormholes", "domain", "projection")
        ));
        descriptors.put(WORMHOLES_BLOCK_CHANGES_PER_SECOND, new IntegrationMetricDescriptor(
                WORMHOLES_BLOCK_CHANGES_PER_SECOND,
                IntegrationMetricType.DOUBLE,
                "blocks-per-second",
                Map.of("plugin", "wormholes", "domain", "network")
        ));
        descriptors.put(WORMHOLES_PACKETS_PER_SECOND, new IntegrationMetricDescriptor(
                WORMHOLES_PACKETS_PER_SECOND,
                IntegrationMetricType.DOUBLE,
                "packets-per-second",
                Map.of("plugin", "wormholes", "domain", "network")
        ));
        descriptors.put(WORMHOLES_SPOOFED_ENTITIES, new IntegrationMetricDescriptor(
                WORMHOLES_SPOOFED_ENTITIES,
                IntegrationMetricType.LONG,
                "entities",
                Map.of("plugin", "wormholes", "domain", "projection")
        ));
        descriptors.put(WORMHOLES_TRAVERSALS_PER_MINUTE, new IntegrationMetricDescriptor(
                WORMHOLES_TRAVERSALS_PER_MINUTE,
                IntegrationMetricType.DOUBLE,
                "traversals-per-minute",
                Map.of("plugin", "wormholes", "domain", "travel")
        ));

        putMetric(descriptors, ADAPT_ABILITY_CACHE_HIT_RATIO, IntegrationMetricType.DOUBLE, "ratio", "adapt", "ability");
        putMetric(descriptors, ADAPT_ABILITY_CHECK_LATENCY_US, IntegrationMetricType.DOUBLE, "us", "adapt", "ability");
        putMetric(descriptors, ADAPT_ABILITY_TIMING_BUDGET, IntegrationMetricType.DOUBLE, "percent", "adapt", "ability");
        putMetric(descriptors, ADAPT_PLAYER_SESSIONS, IntegrationMetricType.INTEGER, "players", "adapt", "runtime");
        putMetric(descriptors, ADAPT_LEARNED_ADAPTATIONS_ONLINE, IntegrationMetricType.INTEGER, "adaptations", "adapt", "runtime");
        putMetric(descriptors, ADAPT_SPATIAL_XP_TICKETS, IntegrationMetricType.INTEGER, "tickets", "adapt", "xp");
        putMetric(descriptors, ADAPT_FX_TIMELINES_ACTIVE, IntegrationMetricType.INTEGER, "timelines", "adapt", "fx");
        putMetric(descriptors, ADAPT_FX_PACKETS_USED, IntegrationMetricType.INTEGER, "packets-per-tick", "adapt", "fx");
        putMetric(descriptors, ADAPT_FX_SHED_BAND, IntegrationMetricType.INTEGER, "band", "adapt", "fx");
        putMetric(descriptors, ADAPT_MINIONS_ACTIVE, IntegrationMetricType.INTEGER, "minions", "adapt", "runtime");
        putMetric(descriptors, ADAPT_PERSISTENCE_QUEUE_DEPTH, IntegrationMetricType.INTEGER, "players", "adapt", "persistence");
        putMetric(descriptors, ADAPT_XP_PER_MINUTE, IntegrationMetricType.DOUBLE, "xp-per-minute", "adapt", "xp");
        putMetric(descriptors, ADAPT_XP_PAYOUT_OPS, IntegrationMetricType.LONG, "ops-per-minute", "adapt", "xp");
        putMetric(descriptors, ADAPT_PROVENANCE_OPS, IntegrationMetricType.LONG, "ops-per-minute", "adapt", "xp");
        putMetric(descriptors, ADAPT_EVENT_HANDLER_OPS, IntegrationMetricType.LONG, "ops-per-minute", "adapt", "runtime");

        putMetric(descriptors, WORMHOLES_PEERS_CONNECTED, IntegrationMetricType.INTEGER, "servers", "wormholes", "network");
        putMetric(descriptors, WORMHOLES_REMOTE_PORTALS, IntegrationMetricType.LONG, "portals", "wormholes", "portals");
        putMetric(descriptors, WORMHOLES_PEER_RTT_MAX_MS, IntegrationMetricType.LONG, "ms", "wormholes", "network");
        putMetric(descriptors, WORMHOLES_WIRE_BYTES_OUT_PER_SECOND, IntegrationMetricType.DOUBLE, "bytes-per-second", "wormholes", "network");
        putMetric(descriptors, WORMHOLES_WIRE_BYTES_IN_PER_SECOND, IntegrationMetricType.DOUBLE, "bytes-per-second", "wormholes", "network");
        putMetric(descriptors, WORMHOLES_COMPRESSION_RATIO_OUT, IntegrationMetricType.DOUBLE, "ratio", "wormholes", "network");
        putMetric(descriptors, WORMHOLES_SIDEBAND_QUEUED_BYTES, IntegrationMetricType.LONG, "bytes", "wormholes", "network");
        putMetric(descriptors, WORMHOLES_SIDEBAND_DROPS_PER_SECOND, IntegrationMetricType.DOUBLE, "drops-per-second", "wormholes", "network");
        putMetric(descriptors, WORMHOLES_VIEW_SUBSCRIPTIONS, IntegrationMetricType.INTEGER, "subscriptions", "wormholes", "projection");
        putMetric(descriptors, WORMHOLES_VIEW_TRACKED_ENTITIES, IntegrationMetricType.INTEGER, "entities", "wormholes", "projection");
        putMetric(descriptors, WORMHOLES_REPLICATED_BLOCKS_PER_SECOND, IntegrationMetricType.DOUBLE, "blocks-per-second", "wormholes", "network");
        putMetric(descriptors, WORMHOLES_RESYNC_REQUESTS_TOTAL, IntegrationMetricType.LONG, "requests", "wormholes", "network");
        putMetric(descriptors, WORMHOLES_TRANSFERS_IN_FLIGHT, IntegrationMetricType.INTEGER, "players", "wormholes", "travel");
        putMetric(descriptors, WORMHOLES_TRANSFERS_FAILED_TOTAL, IntegrationMetricType.LONG, "transfers", "wormholes", "travel");

        putMetric(descriptors, HOLOUI_SESSION_HOLDERS, IntegrationMetricType.INTEGER, "players", "holoui", "sessions");
        putMetric(descriptors, HOLOUI_MENUS_OPEN, IntegrationMetricType.INTEGER, "menus", "holoui", "sessions");
        putMetric(descriptors, HOLOUI_PREVIEWS_OPEN, IntegrationMetricType.INTEGER, "previews", "holoui", "sessions");
        putMetric(descriptors, HOLOUI_DISPLAY_ENTITIES, IntegrationMetricType.INTEGER, "entities", "holoui", "rendering");
        putMetric(descriptors, HOLOUI_DISPLAY_ENTITIES_VISIBLE, IntegrationMetricType.INTEGER, "entities", "holoui", "rendering");
        putMetric(descriptors, HOLOUI_MENU_DEFINITIONS, IntegrationMetricType.INTEGER, "menus", "holoui", "config");
        putMetric(descriptors, HOLOUI_PACKETS_PER_SECOND, IntegrationMetricType.DOUBLE, "packets-per-second", "holoui", "rendering");
        putMetric(descriptors, HOLOUI_SPAWNS_PER_SECOND, IntegrationMetricType.DOUBLE, "entities-per-second", "holoui", "rendering");
        putMetric(descriptors, HOLOUI_TICK_MS, IntegrationMetricType.DOUBLE, "ms-per-second", "holoui", "sessions");
        putMetric(descriptors, HOLOUI_PREVIEW_REFRESH_PER_SECOND, IntegrationMetricType.DOUBLE, "ops-per-second", "holoui", "sessions");
        putMetric(descriptors, HOLOUI_BUILDER_SERVER_RUNNING, IntegrationMetricType.INTEGER, "boolean", "holoui", "config");

        putMetric(descriptors, HIDDENORE_BLOCKS_BROKEN_PER_SECOND, IntegrationMetricType.DOUBLE, "blocks-per-second", "hiddenore", "mining");
        putMetric(descriptors, HIDDENORE_DROPS_INJECTED_PER_SECOND, IntegrationMetricType.DOUBLE, "drops-per-second", "hiddenore", "mining");
        putMetric(descriptors, HIDDENORE_VEINS_DISCOVERED_PER_SECOND, IntegrationMetricType.DOUBLE, "veins-per-second", "hiddenore", "veins");
        putMetric(descriptors, HIDDENORE_VEIN_CHUNKS_COMPUTED_PER_SECOND, IntegrationMetricType.DOUBLE, "chunks-per-second", "hiddenore", "veins");
        putMetric(descriptors, HIDDENORE_VEIN_CACHE_CHUNKS, IntegrationMetricType.INTEGER, "chunks", "hiddenore", "veins");
        putMetric(descriptors, HIDDENORE_PDC_READS_PER_SECOND, IntegrationMetricType.DOUBLE, "ops-per-second", "hiddenore", "provenance");
        putMetric(descriptors, HIDDENORE_PDC_WRITES_PER_SECOND, IntegrationMetricType.DOUBLE, "ops-per-second", "hiddenore", "provenance");
        putMetric(descriptors, HIDDENORE_ORE_REMOVAL_ENABLED, IntegrationMetricType.INTEGER, "boolean", "hiddenore", "generation");
        putMetric(descriptors, HIDDENORE_ORE_REMOVAL_BLOCKS_PER_SECOND, IntegrationMetricType.DOUBLE, "blocks-per-second", "hiddenore", "generation");
        putMetric(descriptors, HIDDENORE_SEEDED_MODE, IntegrationMetricType.INTEGER, "boolean", "hiddenore", "veins");
        putMetric(descriptors, HIDDENORE_DROP_RULES, IntegrationMetricType.INTEGER, "rules", "hiddenore", "config");
        putMetric(descriptors, HIDDENORE_CONFIG_RELOADS_TOTAL, IntegrationMetricType.LONG, "reloads", "hiddenore", "config");

        putMetric(descriptors, BILETOOLS_WATCHED_JARS, IntegrationMetricType.INTEGER, "jars", "biletools", "watcher");
        putMetric(descriptors, BILETOOLS_DIRTY_PLUGINS, IntegrationMetricType.INTEGER, "plugins", "biletools", "watcher");
        putMetric(descriptors, BILETOOLS_RELOADS_TOTAL, IntegrationMetricType.LONG, "reloads", "biletools", "reload");
        putMetric(descriptors, BILETOOLS_LAST_RELOAD_MS, IntegrationMetricType.LONG, "ms", "biletools", "reload");
        putMetric(descriptors, BILETOOLS_REMOTE_SLAVE_ONLINE, IntegrationMetricType.INTEGER, "boolean", "biletools", "remote");

        return Map.copyOf(descriptors);
    }

    private static void putMetric(
            Map<String, IntegrationMetricDescriptor> descriptors,
            String key,
            IntegrationMetricType type,
            String unit,
            String plugin,
            String domain
    ) {
        descriptors.put(key, new IntegrationMetricDescriptor(
                key,
                type,
                unit,
                Map.of("plugin", plugin, "domain", domain)
        ));
    }

    private static void addIrisDescriptors(Map<String, IntegrationMetricDescriptor> descriptors) {
        putIris(descriptors, IRIS_WORLD_COUNT, IntegrationMetricType.INTEGER, "worlds", "lifecycle", "count");
        putIris(descriptors, IRIS_ENGINE_ACTIVE, IntegrationMetricType.INTEGER, "engines", "lifecycle", "count");
        putIris(descriptors, IRIS_ENGINE_CLOSING, IntegrationMetricType.INTEGER, "engines", "lifecycle", "count");
        putIris(descriptors, IRIS_ENGINE_FAILED, IntegrationMetricType.INTEGER, "engines", "lifecycle", "count");
        putIris(descriptors, IRIS_ENGINE_STUDIO, IntegrationMetricType.INTEGER, "engines", "lifecycle", "count");
        putIris(descriptors, IRIS_ENGINE_PENDING_REGISTRATIONS, IntegrationMetricType.INTEGER, "engines", "lifecycle", "count");
        putIris(descriptors, IRIS_LOADED_CHUNKS, IntegrationMetricType.LONG, "chunks", "world", "sum");
        putIris(descriptors, IRIS_LOADED_ENTITIES, IntegrationMetricType.LONG, "entities", "world", "sum");
        putIris(descriptors, IRIS_ENTITY_SATURATION, IntegrationMetricType.DOUBLE, "ratio", "world", "max");
        putIris(descriptors, IRIS_CHUNKS_GENERATED_SESSION, IntegrationMetricType.LONG, "chunks", "generation", "sum");
        putIris(descriptors, IRIS_CHUNKS_GENERATED_TOTAL, IntegrationMetricType.LONG, "chunks", "generation", "sum");
        putIris(descriptors, IRIS_CHUNKS_PER_SECOND, IntegrationMetricType.DOUBLE, "chunks-per-second", "generation", "sum");
        putIris(descriptors, IRIS_BLOCK_UPDATES_PER_SECOND, IntegrationMetricType.LONG, "updates-per-second", "generation", "sum");
        putIris(descriptors, IRIS_ENGINE_PARALLELISM, IntegrationMetricType.INTEGER, "workers", "generation", "sum");
        putIris(descriptors, IRIS_GENERATION_ACTIVE_LEASES, IntegrationMetricType.INTEGER, "leases", "generation", "sum");
        putIris(descriptors, IRIS_HOTLOADS_TOTAL, IntegrationMetricType.LONG, "hotloads", "lifecycle", "sum");
        putIris(descriptors, IRIS_MAINTENANCE_ACTIVE_TASKS, IntegrationMetricType.INTEGER, "tasks", "maintenance", "count");
        putIris(descriptors, IRIS_MAINTENANCE_WORKERS, IntegrationMetricType.INTEGER, "workers", "maintenance", "count");
        putIris(descriptors, IRIS_MANTLE_RESIDENT_PLATES, IntegrationMetricType.LONG, "plates", "mantle", "sum");
        putIris(descriptors, IRIS_MANTLE_QUEUED_PLATES, IntegrationMetricType.LONG, "plates", "mantle", "sum");
        putIris(descriptors, IRIS_MANTLE_IDLE_AVERAGE_MS, IntegrationMetricType.DOUBLE, "ms", "mantle", "mean");
        putIris(descriptors, IRIS_MANTLE_IDLE_MAX_MS, IntegrationMetricType.DOUBLE, "ms", "mantle", "max");
        putIris(descriptors, IRIS_MANTLE_IDLE_MIN_MS, IntegrationMetricType.DOUBLE, "ms", "mantle", "min");
        putIris(descriptors, IRIS_MANTLE_HEAP_USAGE, IntegrationMetricType.DOUBLE, "ratio", "mantle", "current");
        putIris(descriptors, IRIS_MANTLE_RECLAIM_URGENCY, IntegrationMetricType.DOUBLE, "ratio", "mantle", "current");

        putCacheDescriptors(descriptors, "", IRIS_CACHE_COUNT, IRIS_CACHE_ENTRIES, IRIS_CACHE_CAPACITY, IRIS_CACHE_USAGE);
        putCacheDescriptors(descriptors, "resource", IRIS_CACHE_RESOURCE_COUNT, IRIS_CACHE_RESOURCE_ENTRIES, IRIS_CACHE_RESOURCE_CAPACITY, IRIS_CACHE_RESOURCE_USAGE);
        putCacheDescriptors(descriptors, "stream-2d", IRIS_CACHE_STREAM_2D_COUNT, IRIS_CACHE_STREAM_2D_ENTRIES, IRIS_CACHE_STREAM_2D_CAPACITY, IRIS_CACHE_STREAM_2D_USAGE);
        putCacheDescriptors(descriptors, "stream-3d", IRIS_CACHE_STREAM_3D_COUNT, IRIS_CACHE_STREAM_3D_ENTRIES, IRIS_CACHE_STREAM_3D_CAPACITY, IRIS_CACHE_STREAM_3D_USAGE);
        putCacheDescriptors(descriptors, "other", IRIS_CACHE_OTHER_COUNT, IRIS_CACHE_OTHER_ENTRIES, IRIS_CACHE_OTHER_CAPACITY, IRIS_CACHE_OTHER_USAGE);

        putIris(descriptors, IRIS_PREGEN_ACTIVE, IntegrationMetricType.INTEGER, "boolean", "pregen", "current");
        putIris(descriptors, IRIS_PREGEN_PAUSED, IntegrationMetricType.INTEGER, "boolean", "pregen", "current");
        putIris(descriptors, IRIS_PREGEN_PROGRESS, IntegrationMetricType.DOUBLE, "percent", "pregen", "current");
        putIris(descriptors, IRIS_PREGEN_GENERATED, IntegrationMetricType.LONG, "chunks", "pregen", "current");
        putIris(descriptors, IRIS_PREGEN_TOTAL, IntegrationMetricType.LONG, "chunks", "pregen", "current");
        putIris(descriptors, IRIS_PREGEN_QUEUE, IntegrationMetricType.LONG, "chunks", "pregen", "current");
        putIris(descriptors, IRIS_PREGEN_THROUGHPUT, IntegrationMetricType.DOUBLE, "chunks-per-second", "pregen", "current");
        putIris(descriptors, IRIS_PREGEN_ETA_MS, IntegrationMetricType.LONG, "ms", "pregen", "current");
        putIris(descriptors, IRIS_PREGEN_ELAPSED_MS, IntegrationMetricType.LONG, "ms", "pregen", "current");
        putIris(descriptors, IRIS_PREGEN_FAILED, IntegrationMetricType.LONG, "chunks", "pregen", "current");

        putTimingDescriptor(descriptors, IRIS_GENERATION_TOTAL_MS, "total");
        putTimingDescriptor(descriptors, IRIS_GENERATION_UPDATES_MS, "updates");
        putTimingDescriptor(descriptors, IRIS_GENERATION_TERRAIN_MS, "terrain");
        putTimingDescriptor(descriptors, IRIS_GENERATION_BIOME_MS, "biome");
        putTimingDescriptor(descriptors, IRIS_GENERATION_POST_MS, "post");
        putTimingDescriptor(descriptors, IRIS_GENERATION_PERFECTION_MS, "perfection");
        putTimingDescriptor(descriptors, IRIS_GENERATION_DECORATION_MS, "decoration");
        putTimingDescriptor(descriptors, IRIS_GENERATION_CAVE_MS, "cave");
        putTimingDescriptor(descriptors, IRIS_GENERATION_DEPOSIT_MS, "deposit");
        putTimingDescriptor(descriptors, IRIS_GENERATION_CARVE_RESOLVE_MS, "carve-resolve");
        putTimingDescriptor(descriptors, IRIS_GENERATION_CARVE_APPLY_MS, "carve-apply");
        putTimingDescriptor(descriptors, IRIS_GENERATION_CONTEXT_PREFILL_MS, "context-prefill");
        putTimingDescriptor(descriptors, IRIS_PREGEN_WAIT_PERMIT_MS, "pregen-wait-permit");
        putTimingDescriptor(descriptors, IRIS_PREGEN_WAIT_ADAPTIVE_MS, "pregen-wait-adaptive");
    }

    private static void putCacheDescriptors(
            Map<String, IntegrationMetricDescriptor> descriptors,
            String cacheType,
            String countKey,
            String entriesKey,
            String capacityKey,
            String usageKey
    ) {
        String domain = cacheType.isBlank() ? "cache" : "cache-" + cacheType;
        putIris(descriptors, countKey, IntegrationMetricType.INTEGER, "caches", domain, "sum");
        putIris(descriptors, entriesKey, IntegrationMetricType.LONG, "entries", domain, "sum");
        putIris(descriptors, capacityKey, IntegrationMetricType.LONG, "entries", domain, "sum");
        putIris(descriptors, usageKey, IntegrationMetricType.DOUBLE, "ratio", domain, "weighted");
    }

    private static void putTimingDescriptor(
            Map<String, IntegrationMetricDescriptor> descriptors,
            String key,
            String stage
    ) {
        descriptors.put(key, new IntegrationMetricDescriptor(
                key,
                IntegrationMetricType.DOUBLE,
                "ms",
                Map.of("plugin", "iris", "domain", "generation-timing", "stage", stage, "aggregation", "max")
        ));
    }

    private static void putIris(
            Map<String, IntegrationMetricDescriptor> descriptors,
            String key,
            IntegrationMetricType type,
            String unit,
            String domain,
            String aggregation
    ) {
        descriptors.put(key, new IntegrationMetricDescriptor(
                key,
                type,
                unit,
                Map.of("plugin", "iris", "domain", domain, "aggregation", aggregation)
        ));
    }

    private static Set<String> buildIrisKeys() {
        return DESCRIPTORS.values().stream()
                .map(IntegrationMetricDescriptor::key)
                .filter(key -> key.startsWith("iris."))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<String> buildIrisWorldKeys() {
        return Set.of(
                IRIS_ENGINE_ACTIVE,
                IRIS_ENGINE_CLOSING,
                IRIS_ENGINE_FAILED,
                IRIS_ENGINE_STUDIO,
                IRIS_LOADED_CHUNKS,
                IRIS_LOADED_ENTITIES,
                IRIS_ENTITY_SATURATION,
                IRIS_CHUNKS_GENERATED_SESSION,
                IRIS_CHUNKS_GENERATED_TOTAL,
                IRIS_CHUNKS_PER_SECOND,
                IRIS_BLOCK_UPDATES_PER_SECOND,
                IRIS_ENGINE_PARALLELISM,
                IRIS_GENERATION_ACTIVE_LEASES,
                IRIS_HOTLOADS_TOTAL,
                IRIS_MANTLE_RESIDENT_PLATES,
                IRIS_MANTLE_QUEUED_PLATES,
                IRIS_MANTLE_IDLE_AVERAGE_MS,
                IRIS_PREGEN_ACTIVE,
                IRIS_PREGEN_PAUSED,
                IRIS_PREGEN_PROGRESS,
                IRIS_PREGEN_GENERATED,
                IRIS_PREGEN_TOTAL,
                IRIS_PREGEN_QUEUE,
                IRIS_PREGEN_THROUGHPUT,
                IRIS_PREGEN_ETA_MS,
                IRIS_PREGEN_ELAPSED_MS,
                IRIS_PREGEN_FAILED,
                IRIS_GENERATION_TOTAL_MS,
                IRIS_GENERATION_UPDATES_MS,
                IRIS_GENERATION_TERRAIN_MS,
                IRIS_GENERATION_BIOME_MS,
                IRIS_GENERATION_POST_MS,
                IRIS_GENERATION_PERFECTION_MS,
                IRIS_GENERATION_DECORATION_MS,
                IRIS_GENERATION_CAVE_MS,
                IRIS_GENERATION_DEPOSIT_MS,
                IRIS_GENERATION_CARVE_RESOLVE_MS,
                IRIS_GENERATION_CARVE_APPLY_MS,
                IRIS_GENERATION_CONTEXT_PREFILL_MS,
                IRIS_PREGEN_WAIT_PERMIT_MS,
                IRIS_PREGEN_WAIT_ADAPTIVE_MS
        );
    }

    private static IntegrationMetricDescriptor abilityDetailDescriptor(String key, AbilityDetailKey abilityDetailKey) {
        String signal = abilityDetailKey.signal();
        boolean timingSignal = ADAPT_ABILITY_DETAIL_EXECUTION_TIMING_MS.equals(signal)
                || ADAPT_ABILITY_DETAIL_GUARD_TIMING_MS.equals(signal);
        IntegrationMetricType type = timingSignal
                ? IntegrationMetricType.DOUBLE
                : IntegrationMetricType.LONG;
        String unit = timingSignal
                ? "ms-per-minute"
                : "ops-per-minute";
        return new IntegrationMetricDescriptor(
                key,
                type,
                unit,
                Map.of(
                        "plugin", "adapt",
                        "domain", "ability-detail",
                        "ability", abilityDetailKey.abilityId(),
                        "signal", signal,
                        "coverage", "event-tick-guarded-callback-inclusive"
                )
        );
    }

    private static AbilityDetailKey parseAbilityDetailKey(String key) {
        if (key == null || !key.startsWith(ADAPT_ABILITY_DETAIL_PREFIX)) {
            return null;
        }

        String detail = key.substring(ADAPT_ABILITY_DETAIL_PREFIX.length());
        int separator = detail.lastIndexOf('.');
        if (separator <= 0 || separator == detail.length() - 1) {
            return null;
        }

        String abilityId = detail.substring(0, separator);
        String signal = detail.substring(separator + 1);
        if (!isAbilityDetailSignal(signal)) {
            return null;
        }
        String normalizedAbilityId;
        try {
            normalizedAbilityId = normalizeAbilityId(abilityId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        if (!abilityId.equals(normalizedAbilityId)) {
            return null;
        }
        return new AbilityDetailKey(abilityId, signal);
    }

    private static boolean isAbilityDetailSignal(String signal) {
        return ADAPT_ABILITY_DETAIL_EXECUTION_OPS.equals(signal)
                || ADAPT_ABILITY_DETAIL_EXECUTION_TIMING_MS.equals(signal)
                || ADAPT_ABILITY_DETAIL_GUARD_CHECKS.equals(signal)
                || ADAPT_ABILITY_DETAIL_GUARD_TIMING_MS.equals(signal);
    }

    private static String normalizeAbilityId(String abilityId) {
        if (abilityId == null || abilityId.isBlank()) {
            throw new IllegalArgumentException("Ability id cannot be blank");
        }

        String source = abilityId.trim().toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(source.length());
        boolean lastWasSeparator = false;
        for (int i = 0; i < source.length(); i++) {
            char character = source.charAt(i);
            boolean allowed = character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '_'
                    || character == '-';
            if (allowed) {
                normalized.append(character);
                lastWasSeparator = false;
            } else if (!lastWasSeparator && !normalized.isEmpty()) {
                normalized.append('-');
                lastWasSeparator = true;
            }
        }

        while (!normalized.isEmpty() && normalized.charAt(normalized.length() - 1) == '-') {
            normalized.setLength(normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Ability id must contain letters or numbers");
        }
        return normalized.toString();
    }

    private record AbilityDetailKey(String abilityId, String signal) {
    }
}
