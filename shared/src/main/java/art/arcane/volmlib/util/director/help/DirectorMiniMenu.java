package art.arcane.volmlib.util.director.help;

import art.arcane.volmlib.util.director.theme.DirectorProduct;
import art.arcane.volmlib.util.director.theme.DirectorTheme;
import art.arcane.volmlib.util.director.runtime.DirectorParameterDescriptor;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class DirectorMiniMenu {
    private DirectorMiniMenu() {
    }

    public static Optional<DirectorHelpPage> resolveHelp(DirectorRuntimeEngine engine, List<String> rawArgs, int pageSize) {
        if (engine == null || engine.getRoot() == null) {
            return Optional.empty();
        }

        List<String> args = rawArgs == null ? List.of() : rawArgs;
        boolean explicitHelp = args.stream().anyMatch(DirectorMiniMenu::isHelpToken);
        if (!explicitHelp && !args.isEmpty()) {
            return Optional.empty();
        }

        int requestedPage = readPage(args).orElse(0);
        DirectorRuntimeNode cursor = engine.getRoot();
        List<String> tokens = stripHelpTokens(args);

        for (String token : tokens) {
            DirectorRuntimeNode child = findBestChild(cursor, token);
            if (child == null) {
                break;
            }

            cursor = child;
            if (cursor.isInvocable()) {
                break;
            }
        }

        DirectorRuntimeNode target = cursor.isInvocable() && cursor.getParent() != null ? cursor.getParent() : cursor;
        List<DirectorRuntimeNode> entries = new ArrayList<>(target.getChildren());
        entries.sort(Comparator.comparing(node -> node.getDescriptor().getName(), String.CASE_INSENSITIVE_ORDER));

        int safePageSize = Math.max(1, pageSize);
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) safePageSize));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        int from = page * safePageSize;
        int to = Math.min(entries.size(), from + safePageSize);
        List<DirectorRuntimeNode> slice = from >= to ? List.of() : entries.subList(from, to);

        return Optional.of(new DirectorHelpPage(target, List.copyOf(slice), page, totalPages));
    }

    public static List<String> render(DirectorHelpPage page, Theme theme) {
        if (page == null || theme == null) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("<font:minecraft:uniform><strikethrough><gradient:" + theme.borderLeft() + ":" + theme.borderRight() + ">[" + spaces(54) + "]</gradient></font>");
        lines.add("<font:minecraft:uniform><gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">" + escapeText(page.title()) + "</gradient></font>");

        if (page.node().getParent() != null) {
            lines.add("<hover:show_text:'Return to parent command group'><click:run_command:" + page.parentCommand() + "><font:minecraft:uniform><" + theme.primaryRight() + ">〈 Back</" + theme.primaryRight() + "></font></click></hover>");
        }

        if (page.entries().isEmpty()) {
            lines.add("<" + theme.muted() + ">No subcommands on this page.</" + theme.muted() + ">");
        } else {
            for (DirectorRuntimeNode node : page.entries()) {
                lines.add(renderNodeLine(node, theme));
            }
        }

        lines.add(renderFooter(page, theme));
        lines.add("<font:minecraft:uniform><strikethrough><gradient:" + theme.borderLeft() + ":" + theme.borderRight() + ">[" + spaces(54) + "]</gradient></font>");
        return lines;
    }

    private static String renderNodeLine(DirectorRuntimeNode node, Theme theme) {
        String clickType = node.isInvocable() ? "suggest_command" : "run_command";
        String clickTarget = node.isInvocable() ? node.path() + " " : node.path() + " help=1";
        String hover = renderNodeHover(node, theme);
        String aliases = renderAliases(node, theme);

        return "<hover:show_text:'" + hover + "'>"
                + "<click:" + clickType + ":" + clickTarget + ">"
                + "<gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">✦ " + escapeText(node.path()) + "</gradient>"
                + "</click></hover>"
                + aliases
                + renderParameterSummary(node, theme);
    }

    private static String renderNodeHover(DirectorRuntimeNode node, Theme theme) {
        String nl = "<reset>\n";
        StringBuilder hover = new StringBuilder();
        hover.append("<").append(theme.primaryRight()).append(">")
                .append(escapeText(node.path()))
                .append("</").append(theme.primaryRight()).append(">")
                .append(nl);

        String description = node.getDescriptor().getDescription();
        if (description == null || description.trim().isEmpty()) {
            description = "No description provided";
        }

        hover.append("<").append(theme.description()).append(">")
                .append(escapeText(description))
                .append("</").append(theme.description()).append(">");

        if (!node.getDescriptor().getAliases().isEmpty()) {
            hover.append(nl)
                    .append("<").append(theme.muted()).append(">Aliases: ")
                    .append(escapeText(String.join(", ", node.getDescriptor().getAliases())))
                    .append("</").append(theme.muted()).append(">");
        }

        if (!node.isInvocable()) {
            hover.append(nl)
                    .append("<").append(theme.optional()).append(">Command group. Click to open.</").append(theme.optional()).append(">");
            return escapeAttr(hover.toString());
        }

        List<DirectorParameterDescriptor> visibleParameters = visibleParameters(node);
        if (visibleParameters.isEmpty()) {
            hover.append(nl)
                    .append("<").append(theme.optional()).append(">No parameters. Click to prefill command.</").append(theme.optional()).append(">");
            return escapeAttr(hover.toString());
        }

        hover.append(nl)
                .append("<").append(theme.optional()).append(">Parameters:</").append(theme.optional()).append(">");
        for (DirectorParameterDescriptor parameter : visibleParameters) {
            hover.append(nl).append(renderParameterHover(parameter, theme));
        }

        return escapeAttr(hover.toString());
    }

    private static String renderParameterHover(DirectorParameterDescriptor parameter, Theme theme) {
        String name = escapeText(parameter.getName());
        String color = parameter.isContextual()
                ? theme.contextual()
                : (parameter.isRequired() ? theme.required() : theme.optional());

        StringBuilder out = new StringBuilder();
        out.append("<").append(color).append(">• ").append(name).append("</").append(color).append(">");

        String description = parameter.getDescription();
        if (description != null && !description.trim().isEmpty()) {
            out.append(" <").append(theme.description()).append(">")
                    .append(escapeText(description))
                    .append("</").append(theme.description()).append(">");
        }

        out.append(" <").append(theme.muted()).append(">(")
                .append(escapeText(parameter.getType().getSimpleName()));
        if (parameter.isContextual()) {
            out.append(", contextual");
        } else if (parameter.isRequired()) {
            out.append(", required");
        } else {
            out.append(", optional");
        }
        if (parameter.getDefaultValue() != null && !parameter.getDefaultValue().isBlank()) {
            out.append(", default=").append(escapeText(parameter.getDefaultValue()));
        }
        out.append(")</").append(theme.muted()).append(">");

        return out.toString();
    }

    private static String renderAliases(DirectorRuntimeNode node, Theme theme) {
        if (node.getDescriptor().getAliases().isEmpty()) {
            return "";
        }

        return " <" + theme.muted() + ">("
                + escapeText(String.join(", ", node.getDescriptor().getAliases()))
                + ")</" + theme.muted() + ">";
    }

    private static String renderParameterSummary(DirectorRuntimeNode node, Theme theme) {
        if (!node.isInvocable()) {
            return "";
        }

        List<DirectorParameterDescriptor> visibleParameters = visibleParameters(node);
        if (visibleParameters.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        for (DirectorParameterDescriptor parameter : visibleParameters) {
            out.append(" ");
            String parameterName = escapeText(parameter.getName());
            if (parameter.isRequired()) {
                out.append("<").append(theme.required()).append(">[")
                        .append(parameterName).append("]</").append(theme.required()).append(">");
            } else {
                out.append("<").append(theme.optional()).append(">[").append(parameterName).append("]</").append(theme.optional()).append(">");
            }
        }

        return out.toString();
    }

    private static List<DirectorParameterDescriptor> visibleParameters(DirectorRuntimeNode node) {
        List<DirectorParameterDescriptor> visible = new ArrayList<>();
        for (DirectorParameterDescriptor parameter : node.getDescriptor().getParameters()) {
            if (!parameter.isContextual()) {
                visible.add(parameter);
            }
        }

        return visible;
    }

    private static String renderFooter(DirectorHelpPage page, Theme theme) {
        StringBuilder line = new StringBuilder();
        if (page.hasPrevious()) {
            line.append("<hover:show_text:'Previous page'>")
                    .append("<click:run_command:")
                    .append(page.previousCommand())
                    .append("><")
                    .append(theme.primaryLeft())
                    .append(">〈 Page ")
                    .append(page.page() - 1)
                    .append("</")
                    .append(theme.primaryLeft())
                    .append("></click></hover> ");
        }

        line.append("<").append(theme.muted()).append(">")
                .append("Page ").append(page.page()).append(" / ").append(page.totalPages())
                .append("</").append(theme.muted()).append(">");

        if (page.hasNext()) {
            line.append(" <hover:show_text:'Next page'>")
                    .append("<click:run_command:")
                    .append(page.nextCommand())
                    .append("><")
                    .append(theme.primaryRight())
                    .append(">Page ")
                    .append(page.page() + 1)
                    .append(" ❭</")
                    .append(theme.primaryRight())
                    .append("></click></hover>");
        }

        return line.toString();
    }

    private static String spaces(int length) {
        if (length <= 0) {
            return "";
        }

        return " ".repeat(length);
    }

    private static List<String> stripHelpTokens(List<String> args) {
        List<String> clean = new ArrayList<>();
        for (String arg : args) {
            if (!isHelpToken(arg)) {
                clean.add(arg);
            }
        }

        return clean;
    }

    private static Optional<Integer> readPage(List<String> args) {
        for (String arg : args) {
            if (!isHelpToken(arg)) {
                continue;
            }

            String raw = arg.substring("help=".length()).trim();
            try {
                int page = Integer.parseInt(raw);
                return Optional.of(Math.max(0, page - 1));
            } catch (NumberFormatException ignored) {
                return Optional.of(0);
            }
        }

        return Optional.empty();
    }

    private static boolean isHelpToken(String value) {
        if (value == null) {
            return false;
        }

        return value.trim().toLowerCase().startsWith("help=");
    }

    private static DirectorRuntimeNode findBestChild(DirectorRuntimeNode node, String token) {
        if (node == null || token == null || token.trim().isEmpty()) {
            return null;
        }

        String needle = token.trim().toLowerCase();
        DirectorRuntimeNode contains = null;
        for (DirectorRuntimeNode child : node.getChildren()) {
            if (child.getDescriptor().getName().equalsIgnoreCase(needle)) {
                return child;
            }

            for (String alias : child.getDescriptor().getAliases()) {
                if (alias.equalsIgnoreCase(needle)) {
                    return child;
                }
            }

            if (contains != null) {
                continue;
            }

            if (child.getDescriptor().getName().toLowerCase().contains(needle) || needle.contains(child.getDescriptor().getName().toLowerCase())) {
                contains = child;
                continue;
            }

            for (String alias : child.getDescriptor().getAliases()) {
                String loweredAlias = alias.toLowerCase();
                if (loweredAlias.contains(needle) || needle.contains(loweredAlias)) {
                    contains = child;
                    break;
                }
            }
        }

        return contains;
    }

    private static String escapeAttr(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String escapeText(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("<", "\\<").replace(">", "\\>");
    }

    public record Theme(
            String primaryLeft,
            String primaryRight,
            String borderLeft,
            String borderRight,
            String description,
            String required,
            String optional,
            String contextual,
            String muted
    ) {
        public static Theme reactBlue() {
            return new Theme("#003366", "#00BFFF", "#001933", "#1f4f80", "#99c2ff", "#ff6666", "#a0b7d8", "#8fe68f", "#7d93b2");
        }

        public static Theme adaptRed() {
            return new Theme("#8b0000", "#ff4d4d", "#4d0000", "#8b1a1a", "#ffc7c7", "#ff3333", "#ff9e9e", "#ffd966", "#d8a0a0");
        }

        public static Theme irisGreen() {
            return new Theme("#0b7d32", "#3ddc84", "#074d1d", "#0f7f3a", "#b9f6ca", "#ff6666", "#9ad8a8", "#f9e07f", "#8ebf9a");
        }

        public static Theme bileGreen() {
            return new Theme("#0a8f3e", "#3bd16f", "#075e29", "#0d7a35", "#c2f7d2", "#ff6666", "#9edfb3", "#ffd966", "#8abf9b");
        }

        public static Theme fromDirectorTheme(DirectorTheme theme) {
            if (theme == null) {
                return reactBlue();
            }

            if (theme.getProduct() == DirectorProduct.HIDDENORE) {
                return new Theme(
                        "#d8d8d8",
                        "#8a8a8a",
                        "#5c5c5c",
                        "#2f2f2f",
                        "#f2f2f2",
                        "#f2c94c",
                        "#d0d0d0",
                        "#ffe082",
                        "#a6a6a6"
                );
            }

            if (theme.getProduct() == DirectorProduct.HOLOUI) {
                return new Theme(
                        "#ffadad",
                        "#a0c4ff",
                        "#ffd6a5",
                        "#caffbf",
                        "#fef3ff",
                        "#ffd6a5",
                        "#bde0fe",
                        "#caffbf",
                        "#d9c7ef"
                );
            }

            String primaryLeft = normalizeHex(theme.getPrimaryHex(), "#1f5f9f");
            String primaryRight = normalizeHex(theme.getSecondaryHex(), "#4f7fd6");
            String accent = normalizeHex(theme.getAccentHex(), "#ffd966");

            return new Theme(
                    primaryLeft,
                    primaryRight,
                    primaryLeft,
                    primaryRight,
                    primaryRight,
                    accent,
                    primaryRight,
                    accent,
                    primaryRight
            );
        }

        private static String normalizeHex(String value, String fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }

            String trimmed = value.trim();
            if (!trimmed.startsWith("#")) {
                return "#" + trimmed;
            }

            return trimmed;
        }
    }

    public record DirectorHelpPage(DirectorRuntimeNode node, List<DirectorRuntimeNode> entries, int pageIndex, int totalPages) {
        public String title() {
            return node.path() + " {" + page() + "/" + totalPages + "}";
        }

        public int page() {
            return pageIndex + 1;
        }

        public boolean hasPrevious() {
            return pageIndex > 0;
        }

        public boolean hasNext() {
            return pageIndex + 1 < totalPages;
        }

        public String previousCommand() {
            return node.path() + " help=" + pageIndex;
        }

        public String nextCommand() {
            return node.path() + " help=" + (pageIndex + 2);
        }

        public String parentCommand() {
            if (node.getParent() == null) {
                return node.path() + " help=1";
            }

            return node.getParent().path() + " help=1";
        }
    }
}
