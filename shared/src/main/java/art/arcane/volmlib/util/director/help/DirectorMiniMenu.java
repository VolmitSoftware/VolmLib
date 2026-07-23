package art.arcane.volmlib.util.director.help;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.theme.DirectorProduct;
import art.arcane.volmlib.util.director.theme.DirectorTheme;
import art.arcane.volmlib.util.director.runtime.DirectorParameterDescriptor;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class DirectorMiniMenu {
    private static final int HEADER_WIDTH = 44;
    private static final int FOOTER_WIDTH = 75;
    private static final int FOOTER_BUTTON_WIDTH = 10;
    private static final int CLEAR_LINES = 19;

    private DirectorMiniMenu() {
    }

    public static void deliver(Object sender, List<String> lines) {
        if (sender == null || lines == null) {
            return;
        }

        boolean renderable = false;
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                renderable = true;
                break;
            }
        }

        if (renderable && isPlayer(sender)) {
            deliverRaw(sender, "\n".repeat(CLEAR_LINES));
        }

        for (String line : lines) {
            deliverLine(sender, line);
        }
    }

    private static boolean isPlayer(Object sender) {
        for (Class<?> type = sender.getClass(); type != null; type = type.getSuperclass()) {
            if (implementsPlayer(type)) {
                return true;
            }
        }

        return false;
    }

    private static boolean implementsPlayer(Class<?> type) {
        for (Class<?> parent : type.getInterfaces()) {
            if (parent.getName().equals("org.bukkit.entity.Player") || implementsPlayer(parent)) {
                return true;
            }
        }

        return false;
    }

    private static void deliverRaw(Object sender, String message) {
        try {
            sender.getClass().getMethod("sendRichMessage", String.class).invoke(sender, message);
            return;
        } catch (Throwable ignored) {
        }

        try {
            sender.getClass().getMethod("sendMessage", String.class).invoke(sender, message);
        } catch (Throwable ignored) {
        }
    }

    private static void deliverLine(Object sender, String line) {
        if (line == null || line.trim().isEmpty()) {
            return;
        }

        try {
            sender.getClass().getMethod("sendRichMessage", String.class).invoke(sender, line);
            return;
        } catch (Throwable ignored) {
        }

        try {
            sender.getClass().getMethod("sendMessage", String.class)
                    .invoke(sender, stripMiniMessage(line));
        } catch (Throwable ignored) {
        }
    }

    static String stripMiniMessage(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder(input.length());
        int index = 0;
        int length = input.length();
        while (index < length) {
            char current = input.charAt(index);

            if (current == '\\' && index + 1 < length) {
                char escaped = input.charAt(index + 1);
                if (escaped == '<' || escaped == '>' || escaped == '\\') {
                    out.append(escaped);
                    index += 2;
                    continue;
                }
                out.append(current);
                index++;
                continue;
            }

            if (current == '<') {
                int scan = index + 1;
                boolean quoted = false;
                while (scan < length) {
                    char inside = input.charAt(scan);
                    if (inside == '\\' && scan + 1 < length) {
                        scan += 2;
                        continue;
                    }
                    if (inside == '\'') {
                        quoted = !quoted;
                        scan++;
                        continue;
                    }
                    if (inside == '>' && !quoted) {
                        break;
                    }
                    scan++;
                }
                if (scan < length) {
                    index = scan + 1;
                    continue;
                }
                out.append(current);
                index++;
                continue;
            }

            out.append(current);
            index++;
        }

        return out.toString();
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

    public static List<String> render(DirectorHelpPage page, Theme theme, DirectorTextResolver resolver) {
        if (page == null || theme == null) {
            return List.of();
        }

        DirectorTextResolver activeResolver = resolver == null ? DirectorTextResolver.ENGLISH : resolver;
        List<String> lines = new ArrayList<>();
        lines.add(renderHeader(page, theme));

        if (page.node().getParent() != null) {
            lines.add(renderBackLink(page, theme, activeResolver));
        }

        if (page.entries().isEmpty()) {
            lines.add("<" + theme.muted() + ">" + escapeText(resolve(activeResolver, DirectorHelpMessages.NO_SUBCOMMANDS)) + "</" + theme.muted() + ">");
        } else {
            for (DirectorRuntimeNode node : page.entries()) {
                lines.add(renderNodeLine(node, theme, activeResolver));
            }
        }

        lines.add(renderFooter(page, theme, activeResolver));
        return lines;
    }

    private static String renderHeader(DirectorHelpPage page, Theme theme) {
        return banner(page.title(), theme);
    }

    public static String banner(String title, Theme theme) {
        int pad = Math.max(1, HEADER_WIDTH - (title.length() + 2) - 4);
        return "<font:minecraft:uniform><strikethrough><gradient:" + theme.borderLeft() + ":" + theme.borderRight() + ">["
                + spaces(pad) + "(((</gradient></strikethrough></font>"
                + " <gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">" + escapeText(title) + "</gradient> "
                + "<font:minecraft:uniform><strikethrough><gradient:" + theme.borderRight() + ":" + theme.borderLeft() + ">)))"
                + spaces(pad) + "]</gradient></strikethrough></font>";
    }

    public static String bar(Theme theme) {
        return "<font:minecraft:uniform><strikethrough><gradient:" + theme.borderRight() + ":" + theme.borderLeft() + ">"
                + spaces(FOOTER_WIDTH) + "</gradient></strikethrough></font>";
    }

    private static String renderBackLink(DirectorHelpPage page, Theme theme, DirectorTextResolver resolver) {
        return "<hover:show_text:'" + escapeAttr(escapeText(resolve(resolver, DirectorHelpMessages.PARENT_HOVER)))
                + "'><click:run_command:" + page.parentCommand() + "><font:minecraft:uniform><" + theme.primaryRight() + ">〈 "
                + escapeText(resolve(resolver, DirectorHelpMessages.BACK)) + "</" + theme.primaryRight() + "></font></click></hover>";
    }

    private static String renderNodeLine(DirectorRuntimeNode node, Theme theme, DirectorTextResolver resolver) {
        String clickType = node.isInvocable() ? "suggest_command" : "run_command";
        String clickTarget = node.isInvocable() ? node.path() + " " : node.path() + " help=1";

        StringBuilder line = new StringBuilder();
        line.append("<hover:show_text:'").append(renderNodeHover(node, theme, resolver)).append("'>")
                .append("<click:").append(clickType).append(":").append(clickTarget).append(">")
                .append("<").append(theme.muted()).append(">⇀</").append(theme.muted()).append("> ")
                .append("<gradient:").append(theme.primaryLeft()).append(":").append(theme.primaryRight()).append(">")
                .append(escapeText(node.getDescriptor().getName())).append("</gradient>")
                .append("</click></hover>");

        if (node.isInvocable()) {
            for (DirectorParameterDescriptor parameter : visibleParameters(node)) {
                line.append(" ").append(renderParameterChip(parameter, theme, resolver));
            }
        } else {
            line.append(" <").append(theme.description()).append(">- ")
                    .append(escapeText(resolve(resolver, DirectorHelpMessages.CATEGORY)))
                    .append("</").append(theme.description()).append(">");
        }

        return line.toString();
    }

    private static String renderNodeHover(DirectorRuntimeNode node, Theme theme, DirectorTextResolver resolver) {
        String nl = "<reset>\n";
        List<DirectorParameterDescriptor> visibleParameters = visibleParameters(node);
        StringBuilder hover = new StringBuilder();
        hover.append("<").append(theme.primaryRight()).append(">")
                .append(escapeText(String.join(", ", node.allNames())))
                .append("</").append(theme.primaryRight()).append(">")
                .append(nl);

        hover.append(renderDescriptionLine(node.getDescriptor().getDescription(), node.getDescriptor().getDescriptionKey(), theme, resolver));

        String usage;
        if (!node.isInvocable()) {
            usage = resolve(resolver, DirectorHelpMessages.COMMAND_GROUP);
        } else if (visibleParameters.isEmpty()) {
            usage = resolve(resolver, DirectorHelpMessages.NO_PARAMETERS);
        } else {
            usage = resolve(resolver, DirectorHelpMessages.PARAMETERS_HOVER);
        }

        hover.append(nl)
                .append("<").append(theme.optional()).append(">✒ <font:minecraft:uniform>")
                .append(escapeText(usage))
                .append("</font></").append(theme.optional()).append(">");

        if (node.isInvocable() && !visibleParameters.isEmpty()) {
            hover.append(nl)
                    .append("<").append(theme.primaryLeft()).append(">✦ <font:minecraft:uniform>")
                    .append(escapeText(renderExample(node, visibleParameters)))
                    .append("</font></").append(theme.primaryLeft()).append(">");
        }

        return escapeAttr(hover.toString());
    }

    private static String renderDescriptionLine(String description, String descriptionKey, Theme theme, DirectorTextResolver resolver) {
        String resolved;
        if (description == null || description.trim().isEmpty()) {
            resolved = resolve(resolver, DirectorHelpMessages.NO_DESCRIPTION);
        } else {
            resolved = resolveDescription(resolver, descriptionKey, description);
        }

        return "<" + theme.description() + ">✎ <font:minecraft:uniform>" + escapeText(resolved) + "</font></" + theme.description() + ">";
    }

    private static String renderExample(DirectorRuntimeNode node, List<DirectorParameterDescriptor> parameters) {
        StringBuilder example = new StringBuilder(node.path());
        for (DirectorParameterDescriptor parameter : parameters) {
            example.append(" ").append(parameter.getName()).append("=");
            if (parameter.getDefaultValue() != null && !parameter.getDefaultValue().isBlank()) {
                example.append(parameter.getDefaultValue());
            } else {
                example.append("<").append(parameter.getType().getSimpleName()).append(">");
            }
        }

        return example.toString();
    }

    private static String renderParameterChip(DirectorParameterDescriptor parameter, Theme theme, DirectorTextResolver resolver) {
        String bracketColor = parameter.isRequired() ? theme.required() : theme.muted();
        String open = parameter.isRequired() ? "[" : "⊰";
        String close = parameter.isRequired() ? "]" : "⊱";

        return "<hover:show_text:'" + renderParameterHover(parameter, theme, resolver) + "'>"
                + "<" + bracketColor + ">" + open + "</" + bracketColor + ">"
                + "<gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">" + escapeText(parameter.getName()) + "</gradient>"
                + "<" + bracketColor + ">" + close + "</" + bracketColor + ">"
                + "</hover>";
    }

    private static String renderParameterHover(DirectorParameterDescriptor parameter, Theme theme, DirectorTextResolver resolver) {
        String nl = "<reset>\n";
        List<String> names = new ArrayList<>();
        names.add(parameter.getName());
        for (String alias : parameter.getAliases()) {
            if (alias != null && !alias.trim().isEmpty()) {
                names.add(alias);
            }
        }

        StringBuilder hover = new StringBuilder();
        hover.append("<").append(theme.primaryRight()).append(">")
                .append(escapeText(String.join(", ", names)))
                .append("</").append(theme.primaryRight()).append(">")
                .append(nl);

        hover.append(renderDescriptionLine(parameter.getDescription(), parameter.getDescriptionKey(), theme, resolver));

        hover.append(nl);
        if (parameter.isRequired()) {
            hover.append("<").append(theme.required()).append(">⚠ <font:minecraft:uniform>")
                    .append(escapeText(resolve(resolver, DirectorHelpMessages.REQUIRED)))
                    .append("</font></").append(theme.required()).append(">");
        } else if (parameter.getDefaultValue() != null && !parameter.getDefaultValue().isBlank()) {
            hover.append("<").append(theme.optional()).append(">✔ <font:minecraft:uniform>")
                    .append(escapeText(resolve(resolver, DirectorHelpMessages.DEFAULT, MessageArgument.untrusted("value", parameter.getDefaultValue()))))
                    .append("</font></").append(theme.optional()).append(">");
        } else {
            hover.append("<").append(theme.optional()).append(">✔ <font:minecraft:uniform>")
                    .append(escapeText(resolve(resolver, DirectorHelpMessages.OPTIONAL)))
                    .append("</font></").append(theme.optional()).append(">");
        }

        hover.append(nl)
                .append("<").append(theme.muted()).append(">✢ <font:minecraft:uniform>")
                .append(escapeText(resolve(resolver, DirectorHelpMessages.PARAMETER_TYPE, MessageArgument.untrusted("type", parameter.getType().getSimpleName()))))
                .append("</font></").append(theme.muted()).append(">");

        return escapeAttr(hover.toString());
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

    private static String renderFooter(DirectorHelpPage page, Theme theme, DirectorTextResolver resolver) {
        StringBuilder line = new StringBuilder();
        int fill = FOOTER_WIDTH;

        if (page.hasPrevious()) {
            fill -= FOOTER_BUTTON_WIDTH;
            line.append("<hover:show_text:'")
                    .append(escapeAttr(escapeText(resolve(resolver, DirectorHelpMessages.PREVIOUS_PAGE))))
                    .append("'><click:run_command:")
                    .append(page.previousCommand())
                    .append("><").append(theme.primaryLeft()).append(">〈 ")
                    .append(escapeText(resolve(resolver, DirectorHelpMessages.PAGE))).append(" ")
                    .append(page.page() - 1)
                    .append("</").append(theme.primaryLeft()).append("></click></hover> ");
        }

        if (page.hasNext()) {
            fill -= FOOTER_BUTTON_WIDTH;
        }

        line.append("<font:minecraft:uniform><strikethrough><gradient:")
                .append(theme.borderRight()).append(":").append(theme.borderLeft()).append(">")
                .append(spaces(fill))
                .append("</gradient></strikethrough></font>");

        if (page.hasNext()) {
            line.append(" <hover:show_text:'")
                    .append(escapeAttr(escapeText(resolve(resolver, DirectorHelpMessages.NEXT_PAGE))))
                    .append("'><click:run_command:")
                    .append(page.nextCommand())
                    .append("><").append(theme.primaryRight()).append(">")
                    .append(escapeText(resolve(resolver, DirectorHelpMessages.PAGE))).append(" ")
                    .append(page.page() + 1)
                    .append(" ❭</").append(theme.primaryRight()).append("></click></hover>");
        }

        return line.toString();
    }

    private static String resolve(DirectorTextResolver resolver, TextKey key, MessageArgument... arguments) {
        MessageArgs.Builder builder = MessageArgs.builder();
        for (MessageArgument argument : arguments) {
            builder.add(argument);
        }
        MessageArgs args = builder.build();

        String resolved = resolver.resolve(key, args);
        return resolved == null ? DirectorTextResolver.ENGLISH.resolve(key, args) : resolved;
    }

    private static String resolveDescription(
            DirectorTextResolver resolver,
            String key,
            String englishDefault
    ) {
        if (key == null || key.isBlank()) {
            return englishDefault;
        }
        return resolve(resolver, TextKey.of(key, englishDefault));
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

            if (!arg.trim().toLowerCase(Locale.ROOT).startsWith("help=")) {
                return Optional.of(0);
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

        String token = value.trim().toLowerCase(Locale.ROOT);
        return token.equals("help") || token.equals("?") || token.startsWith("help=");
    }

    private static DirectorRuntimeNode findBestChild(DirectorRuntimeNode node, String token) {
        if (node == null || token == null || token.trim().isEmpty()) {
            return null;
        }

        String needle = token.trim().toLowerCase(Locale.ROOT);
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

            if (child.getDescriptor().getName().toLowerCase(Locale.ROOT).contains(needle)
                    || needle.contains(child.getDescriptor().getName().toLowerCase(Locale.ROOT))) {
                contains = child;
                continue;
            }

            for (String alias : child.getDescriptor().getAliases()) {
                String loweredAlias = alias.toLowerCase(Locale.ROOT);
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

    public static String escapeText(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\").replace("<", "\\<");
    }

    public record Theme(
            String primaryLeft,
            String primaryRight,
            String borderLeft,
            String borderRight,
            String description,
            String required,
            String optional,
            String muted
    ) {
        public static Theme reactBlue() {
            return new Theme("#003366", "#00BFFF", "#001933", "#1f4f80", "#99c2ff", "#ff6666", "#a0b7d8", "#7d93b2");
        }

        public static Theme adaptRed() {
            return new Theme("#8b0000", "#ff4d4d", "#4d0000", "#8b1a1a", "#ffc7c7", "#ff3333", "#ff9e9e", "#d8a0a0");
        }

        public static Theme irisGreen() {
            return new Theme("#5ef288", "#32bfad", "#34eb6b", "#32bfad", "#6ad97d", "#ff5555", "#9de5b6", "#46826a");
        }

        public static Theme bileGreen() {
            return new Theme("#0a8f3e", "#3bd16f", "#075e29", "#0d7a35", "#c2f7d2", "#ff6666", "#9edfb3", "#8abf9b");
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
            if (totalPages <= 1) {
                return node.path();
            }

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
