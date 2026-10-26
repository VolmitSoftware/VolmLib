package art.arcane.volmlib.util.director.theme;

public final class DirectorThemes {
    public static final DirectorTheme DREAD = new DirectorTheme(
            DirectorProduct.DREAD,
            "#FF725E",
            "#FFD166",
            "#FFD166",
            "minecraft:block.note_block.chime",
            "minecraft:block.note_block.bass"
    );

    public static final DirectorTheme IRIS = new DirectorTheme(
            DirectorProduct.IRIS,
            "#34eb6b",
            "#32bfad",
            "#5ef288",
            "minecraft:block.amethyst_cluster.break",
            "minecraft:block.beacon.deactivate"
    );

    public static final DirectorTheme REACT = new DirectorTheme(
            DirectorProduct.REACT,
            "#0b2a4a",
            "#003366",
            "#1e6fb8",
            "minecraft:block.amethyst_cluster.break",
            "minecraft:block.respawn_anchor.deplete"
    );

    public static final DirectorTheme ADAPT = new DirectorTheme(
            DirectorProduct.ADAPT,
            "#b30000",
            "#8b1a1a",
            "#ff5a5a",
            "minecraft:block.amethyst_cluster.break",
            "minecraft:block.respawn_anchor.deplete"
    );

    public static final DirectorTheme BILE = new DirectorTheme(
            DirectorProduct.BILE,
            "#2f9e44",
            "#1b5e20",
            "#74c69d",
            "minecraft:block.amethyst_cluster.break",
            "minecraft:block.beacon.deactivate"
    );

    public static final DirectorTheme HIDDENORE = new DirectorTheme(
            DirectorProduct.HIDDENORE,
            "#8a8a8a",
            "#4a4a4a",
            "#f2c94c",
            "minecraft:block.amethyst_cluster.break",
            "minecraft:block.respawn_anchor.deplete"
    );

    public static final DirectorTheme WORMHOLES = new DirectorTheme(
            DirectorProduct.WORMHOLES,
            "#d4af37",
            "#8c7a45",
            "#f4d35e",
            "minecraft:block.amethyst_cluster.break",
            "minecraft:block.note_block.bass"
    );

    public static final DirectorTheme GLOSS = new DirectorTheme(
            DirectorProduct.GLOSS,
            "#8a2be2",
            "#5c19c4",
            "#b47aff",
            "minecraft:block.amethyst_cluster.break",
            "minecraft:block.respawn_anchor.deplete"
    );

    public static final DirectorTheme SHAPEDPORTALS = new DirectorTheme(
            DirectorProduct.SHAPEDPORTALS,
            "#6f35c5",
            "#35135f",
            "#c778ff",
            "minecraft:block.amethyst_cluster.break",
            "minecraft:block.respawn_anchor.deplete"
    );

    public static final DirectorTheme COFFER = new DirectorTheme(
            DirectorProduct.COFFER,
            "#D99B5E",
            "#8A603C",
            "#F0D69B",
            "minecraft:block.barrel.close",
            "minecraft:block.chest.locked"
    );

    public static final DirectorTheme BYTECOMPANIONS = new DirectorTheme(
            DirectorProduct.BYTECOMPANIONS,
            "#ffcc00",
            "#ff9900",
            "#ffdb80",
            "minecraft:block.amethyst_cluster.break",
            "minecraft:block.respawn_anchor.deplete"
    );

    public static final DirectorTheme VIGIL = new DirectorTheme(
            DirectorProduct.VIGIL,
            "#FF956B",
            "#FFD28A",
            "#FFD28A",
            "minecraft:block.amethyst_cluster.break",
            "minecraft:block.beacon.deactivate"
    );

    public static final DirectorTheme ENFOLD = new DirectorTheme(
            DirectorProduct.ENFOLD,
            "#B5E3C6",
            "#70D6AC",
            "#F2BD66",
            "minecraft:block.amethyst_cluster.break",
            "minecraft:block.beacon.deactivate"
    );

    public static final DirectorTheme UNDERTOW = new DirectorTheme(
            DirectorProduct.UNDERTOW,
            "#48C9A0",
            "#153F3B",
            "#91E5D2",
            "minecraft:block.amethyst_cluster.break",
            "minecraft:block.beacon.deactivate"
    );

    public static final DirectorTheme CINDER = new DirectorTheme(
            DirectorProduct.CINDER,
            "#e46a32",
            "#f3bf62",
            "#f3bf62",
            "minecraft:block.amethyst_cluster.break",
            "minecraft:block.beacon.deactivate"
    );

    public static final DirectorTheme KEYSTONE = new DirectorTheme(
            DirectorProduct.KEYSTONE,
            "#E89B62",
            "#70452F",
            "#F4D58D",
            "minecraft:block.amethyst_block.chime",
            "minecraft:block.note_block.bass"
    );

    public static final DirectorTheme ECHELON = new DirectorTheme(
            DirectorProduct.ECHELON,
            "#4f63e6",
            "#2f3bb0",
            "#aab7ff",
            "minecraft:entity.experience_orb.pickup",
            "minecraft:block.note_block.bass"
    );

    public static final DirectorTheme SKYPRIME = new DirectorTheme(
            DirectorProduct.SKYPRIME,
            "#00d8e8",
            "#087e8b",
            "#ffffff",
            "minecraft:entity.experience_orb.pickup",
            "minecraft:block.note_block.bass"
    );

    private DirectorThemes() {
    }

    public static DirectorTheme forProduct(DirectorProduct product) {
        if (product == null) {
            return REACT;
        }

        return switch (product) {
            case DREAD -> DREAD;
            case CINDER -> CINDER;
            case IRIS -> IRIS;
            case REACT -> REACT;
            case ADAPT -> ADAPT;
            case BILE -> BILE;
            case HIDDENORE -> HIDDENORE;
            case WORMHOLES -> WORMHOLES;
            case GLOSS -> GLOSS;
            case SHAPEDPORTALS -> SHAPEDPORTALS;
            case COFFER -> COFFER;
            case BYTECOMPANIONS -> BYTECOMPANIONS;
            case VIGIL -> VIGIL;
            case ENFOLD -> ENFOLD;
            case UNDERTOW -> UNDERTOW;
            case KEYSTONE -> KEYSTONE;
            case ECHELON -> ECHELON;
            case SKYPRIME -> SKYPRIME;
        };
    }
}
