package art.arcane.volmlib.util.director.theme;

public final class DirectorTheme {
    private final DirectorProduct product;
    private final String primaryHex;
    private final String secondaryHex;
    private final String accentHex;
    private final String successSound;
    private final String errorSound;

    public DirectorTheme(
            DirectorProduct product,
            String primaryHex,
            String secondaryHex,
            String accentHex,
            String successSound,
            String errorSound
    ) {
        this.product = product;
        this.primaryHex = primaryHex;
        this.secondaryHex = secondaryHex;
        this.accentHex = accentHex;
        this.successSound = successSound;
        this.errorSound = errorSound;
    }

    public DirectorProduct getProduct() {
        return product;
    }

    public String getPrimaryHex() {
        return primaryHex;
    }

    public String getSecondaryHex() {
        return secondaryHex;
    }

    public String getAccentHex() {
        return accentHex;
    }

    public String getSuccessSound() {
        return successSound;
    }

    public String getErrorSound() {
        return errorSound;
    }
}
