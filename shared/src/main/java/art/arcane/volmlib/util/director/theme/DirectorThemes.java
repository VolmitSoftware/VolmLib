package art.arcane.volmlib.util.director.theme;

public final class DirectorThemes {
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

    public static final DirectorTheme HOLOUI = new DirectorTheme(
            DirectorProduct.HOLOUI,
            "#ffadad",
            "#a0c4ff",
            "#ffd6a5",
            "minecraft:block.amethyst_cluster.break",
            "minecraft:block.respawn_anchor.deplete"
    );

    private DirectorThemes() {
    }

    public static DirectorTheme forProduct(DirectorProduct product) {
        if (product == null) {
            return REACT;
        }

        return switch (product) {
            case IRIS -> IRIS;
            case REACT -> REACT;
            case ADAPT -> ADAPT;
            case BILE -> BILE;
            case HIDDENORE -> HIDDENORE;
            case HOLOUI -> HOLOUI;
        };
    }
}
