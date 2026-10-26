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
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class DirectorMiniMenu {
    public static final int CHAT_LINE_COUNT = 20;
    public static final int MAX_ENTRIES_PER_PAGE = CHAT_LINE_COUNT - 2;

    private static final int CHAT_WIDTH = 320;
    private static final int WIDE_GLYPH_WIDTH = 9;
    private static final int FOOTER_WIDTH = 75;
    private static final int FONT_SPACE_WIDTH = 4;
    private static final int HEADER_ORNAMENT_WIDTH = 28;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private DirectorMiniMenu() {
    }

    public static void deliver(Object sender, DirectorHelpPage page, Theme theme, DirectorTextResolver resolver) {
        if (sender == null || page == null) {
            return;
        }

        if (isPlayer(sender)) {
            deliver(sender, render(page, theme, resolver));
            return;
        }

        for (String line : renderConsole(page, resolver)) {
            deliverPlainLine(sender, line);
        }
    }

    public static void deliver(Object sender, DirectorHelpPage page, Theme theme, DirectorTextResolver resolver,
                               Predicate<DirectorRuntimeNode> permitted) {
        if (sender == null || page == null) {
            return;
        }

        deliver(sender, page.withEntries(page.entries().stream().filter(permitted).toList()), theme, resolver);
    }

    public static void deliverContent(Object sender, ContentMenu menu, Theme theme, DirectorTextResolver resolver) {
        if (sender == null || menu == null) {
            return;
        }

        if (isPlayer(sender)) {
            deliver(sender, renderContent(menu, theme, resolver));
            return;
        }

        for (String line : renderContentConsole(menu)) {
            deliverPlainLine(sender, line);
        }
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
            deliverRaw(sender, "\n".repeat(CHAT_LINE_COUNT - 1));
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
        if (sender instanceof CommandSender commandSender) {
            ComponentMessenger.sendLiteral(commandSender, message);
            return;
        }
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

        if (sender instanceof CommandSender commandSender) {
            ComponentMessenger.send(commandSender, ComponentText.component(MINI_MESSAGE.deserialize(line)));
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

    private static void deliverPlainLine(Object sender, String line) {
        if (line == null || line.trim().isEmpty()) {
            return;
        }

        try {
            sender.getClass().getMethod("sendMessage", String.class).invoke(sender, line);
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

    public static Optional<DirectorHelpPage> resolveHelp(DirectorRuntimeEngine engine, List<String> rawArgs) {
        return resolveHelp(engine, rawArgs, MAX_ENTRIES_PER_PAGE);
    }

    static Optional<DirectorHelpPage> resolveHelp(DirectorRuntimeEngine engine, List<String> rawArgs, int pageSize) {
        int safePageSize = Math.max(1, pageSize);
        if (engine == null || engine.getRoot() == null) {
            return Optional.empty();
        }

        List<String> args = rawArgs == null ? List.of() : rawArgs;
        boolean explicitHelp = args.stream().anyMatch(DirectorMiniMenu::isHelpToken);
        if (!explicitHelp && !args.isEmpty()) {
            return resolveImplicitHelp(engine.getRoot(), args, safePageSize);
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
        return Optional.of(buildPage(target, requestedPage, safePageSize));
    }

    private static Optional<DirectorHelpPage> resolveImplicitHelp(
            DirectorRuntimeNode root,
            List<String> args,
            int pageSize
    ) {
        DirectorRuntimeNode cursor = root;

        for (String token : args) {
            if (token == null || token.trim().isEmpty()) {
                continue;
            }

            DirectorRuntimeNode child = findBestChild(cursor, token);
            if (child == null) {
                return Optional.empty();
            }

            cursor = child;
        }

        if (cursor.isInvocable()) {
            return Optional.empty();
        }

        return Optional.of(buildPage(cursor, 0, pageSize));
    }

    private static DirectorHelpPage buildPage(DirectorRuntimeNode target, int requestedPage, int pageSize) {
        return new DirectorHelpPage(target, sortedChildren(target), requestedPage, pageSize);
    }

    private static List<DirectorRuntimeNode> sortedChildren(DirectorRuntimeNode node) {
        List<DirectorRuntimeNode> children = new ArrayList<>();
        for (DirectorRuntimeNode child : node.getChildren()) {
            if (!child.getDescriptor().isHidden()) {
                children.add(child);
            }
        }
        children.sort(Comparator.comparing(child -> child.getDescriptor().getName(), String.CASE_INSENSITIVE_ORDER));
        return children;
    }

    public static List<String> render(DirectorHelpPage page, Theme theme, DirectorTextResolver resolver) {
        if (page == null || theme == null) {
            return List.of();
        }

        DirectorTextResolver activeResolver = resolver == null ? DirectorTextResolver.ENGLISH : resolver;
        boolean backLink = page.node().getParent() != null;
        List<String> entryLines = renderNodeLines(page, theme, activeResolver);
        ContentPage window = helpWindow(page, entryLines);
        List<String> lines = new ArrayList<>(window.endIndex() - window.startIndex() + 3);
        lines.add(renderHeader(page, theme));

        if (backLink) {
            lines.add(renderBackLink(page, theme, activeResolver));
        }

        if (entryLines.isEmpty()) {
            lines.add("<" + theme.muted() + ">" + escapeText(resolve(activeResolver, DirectorHelpMessages.NO_SUBCOMMANDS)) + "</" + theme.muted() + ">");
        } else {
            lines.addAll(entryLines.subList(window.startIndex(), window.endIndex()));
        }

        lines.add(renderPaginationBar(
                window.hasPrevious(),
                window.hasNext(),
                window.page() - 1,
                window.page() + 1,
                page.pageCommand(window.page() - 1),
                page.pageCommand(window.page() + 1),
                theme,
                activeResolver
        ));
        return lines;
    }

    public static ContentPage helpWindow(DirectorHelpPage page, Theme theme, DirectorTextResolver resolver) {
        Objects.requireNonNull(page, "page");
        DirectorTextResolver activeResolver = resolver == null ? DirectorTextResolver.ENGLISH : resolver;
        return helpWindow(page, renderNodeLines(page, Objects.requireNonNull(theme, "theme"), activeResolver));
    }

    private static ContentPage helpWindow(DirectorHelpPage page, List<String> entryLines) {
        return paginate(entryLines, page.pageIndex() + 1, page.pageSize(), reservedLines(page.node().getParent() != null));
    }

    private static List<String> renderNodeLines(DirectorHelpPage page, Theme theme, DirectorTextResolver resolver) {
        List<String> lines = new ArrayList<>(page.entries().size());
        for (DirectorRuntimeNode node : page.entries()) {
            lines.add(renderNodeLine(node, theme, resolver));
        }
        return lines;
    }

    public static List<String> renderContent(ContentMenu menu, Theme theme, DirectorTextResolver resolver) {
        if (menu == null || theme == null) {
            return List.of();
        }

        DirectorTextResolver activeResolver = resolver == null ? DirectorTextResolver.ENGLISH : resolver;
        ContentPage page = menu.page();
        ArrayList<String> lines = new ArrayList<>();
        lines.add(banner(menu.title(), theme));
        if (!menu.parentCommand().isBlank()) {
            lines.add(backLink(menu.parentCommand(), theme, activeResolver));
        }
        if (menu.entries().isEmpty()) {
            if (!menu.emptyLine().isBlank()) {
                lines.add(menu.emptyLine());
            }
        } else {
            lines.addAll(menu.entries().subList(page.startIndex(), page.endIndex()));
        }
        lines.add(paginationBar(page, menu.command(), theme, activeResolver));
        return List.copyOf(lines);
    }

    public static List<String> renderConsole(DirectorHelpPage page, DirectorTextResolver resolver) {
        if (page == null) {
            return List.of();
        }

        return renderConsole(page.node(), page.entries(), resolver);
    }

    private static List<String> renderConsole(DirectorRuntimeNode node, List<DirectorRuntimeNode> entries, DirectorTextResolver resolver) {
        DirectorTextResolver activeResolver = resolver == null ? DirectorTextResolver.ENGLISH : resolver;
        List<String> lines = new ArrayList<>();
        lines.add("--- " + node.path() + " ---");

        if (entries.isEmpty()) {
            lines.add(resolve(activeResolver, DirectorHelpMessages.NO_SUBCOMMANDS));
            return lines;
        }

        for (DirectorRuntimeNode entry : entries) {
            lines.add(renderConsoleNodeLine(entry, activeResolver));
        }

        return lines;
    }

    public static List<String> renderContentConsole(ContentMenu menu) {
        if (menu == null) {
            return List.of();
        }

        ArrayList<String> lines = new ArrayList<>();
        lines.add("--- " + stripMiniMessage(menu.title()) + " ---");
        if (menu.entries().isEmpty()) {
            if (!menu.emptyLine().isBlank()) {
                lines.add(stripMiniMessage(menu.emptyLine()));
            }
            return List.copyOf(lines);
        }
        for (String entry : menu.entries()) {
            lines.add(stripMiniMessage(entry));
        }
        return List.copyOf(lines);
    }

    private static String renderConsoleNodeLine(DirectorRuntimeNode node, DirectorTextResolver resolver) {
        StringBuilder line = new StringBuilder(node.getDescriptor().getName());

        if (!node.isInvocable()) {
            return line.append(" - ").append(resolve(resolver, DirectorHelpMessages.CATEGORY)).toString();
        }

        for (DirectorParameterDescriptor parameter : visibleParameters(node)) {
            line.append(' ').append(renderConsoleParameter(parameter));
        }

        return line.append(" - ")
                .append(renderConsoleDescription(node.getDescriptor().getDescription(), node.getDescriptor().getDescriptionKey(), resolver))
                .toString();
    }

    private static String renderConsoleParameter(DirectorParameterDescriptor parameter) {
        if (parameter.isContextual()) {
            return "[" + parameter.getName() + "=...]";
        }

        if (parameter.isRequired()) {
            return "<" + parameter.getName() + "=...>";
        }

        if (parameter.getDefaultValue() != null && !parameter.getDefaultValue().isBlank()) {
            return "[" + parameter.getName() + "=" + parameter.getDefaultValue() + "]";
        }

        return "[" + parameter.getName() + "]";
    }

    private static String renderConsoleDescription(String description, String descriptionKey, DirectorTextResolver resolver) {
        if (description == null || description.trim().isEmpty()) {
            return resolve(resolver, DirectorHelpMessages.NO_DESCRIPTION);
        }

        return resolveDescription(resolver, descriptionKey, description);
    }

    private static String renderHeader(DirectorHelpPage page, Theme theme) {
        return banner(page.title(), theme);
    }

    public static String banner(String title, Theme theme) {
        String activeTitle = title == null ? "" : title;
        int remainingWidth = (FOOTER_WIDTH * FONT_SPACE_WIDTH) - textWidth(activeTitle) - HEADER_ORNAMENT_WIDTH;
        int padding = Math.max(1, (remainingWidth + FONT_SPACE_WIDTH) / (FONT_SPACE_WIDTH * 2));
        return "<font:minecraft:uniform><strikethrough><gradient:" + theme.borderLeft() + ":" + theme.borderRight() + ">["
                + spaces(padding) + "(((</gradient></strikethrough></font>"
                + " <gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">" + escapeText(activeTitle) + "</gradient> "
                + "<font:minecraft:uniform><strikethrough><gradient:" + theme.borderRight() + ":" + theme.borderLeft() + ">)))"
                + spaces(padding) + "]</gradient></strikethrough></font>";
    }

    public static String version(String pluginName, String version, Theme theme) {
        return "<gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">"
                + escapeText(pluginName + " v" + version) + "</gradient>";
    }

    public static String banner(String title, ContentPage page, Theme theme) {
        if (page == null) {
            return banner(title, theme);
        }

        return banner(page.title(title), theme);
    }

    public static String bar(Theme theme) {
        return "<font:minecraft:uniform><strikethrough><gradient:" + theme.borderRight() + ":" + theme.borderLeft() + ">"
                + spaces(FOOTER_WIDTH) + "</gradient></strikethrough></font>";
    }

    private static String renderBackLink(DirectorHelpPage page, Theme theme, DirectorTextResolver resolver) {
        return backLink(page.parentCommand(), theme, resolver);
    }

    public static String backLink(String parentCommand, Theme theme, DirectorTextResolver resolver) {
        String command = Objects.requireNonNull(parentCommand, "parentCommand");
        Theme activeTheme = Objects.requireNonNull(theme, "theme");
        DirectorTextResolver activeResolver = resolver == null ? DirectorTextResolver.ENGLISH : resolver;
        return "<hover:show_text:'" + escapeAttr(escapeText(resolve(activeResolver, DirectorHelpMessages.PARENT_HOVER)))
                + "'><click:run_command:" + command + "><font:minecraft:uniform><" + activeTheme.primaryRight() + ">〈 "
                + escapeText(resolve(activeResolver, DirectorHelpMessages.BACK)) + "</" + activeTheme.primaryRight()
                + "></font></click></hover>";
    }

    private static String renderNodeLine(DirectorRuntimeNode node, Theme theme, DirectorTextResolver resolver) {
        boolean runsDirectly = node.isInvocable() && visibleParameters(node).isEmpty();
        String clickType = node.isInvocable() && !runsDirectly ? "suggest_command" : "run_command";
        String clickTarget = node.isInvocable() ? node.path() + (runsDirectly ? "" : " ") : node.path() + " help=1";

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
        boolean required = parameter.isRequired() && !parameter.isContextual();
        String bracketColor = required ? theme.required() : theme.muted();
        String open = required ? "[" : "⊰";
        String close = required ? "]" : "⊱";

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
        if (parameter.isRequired() && !parameter.isContextual()) {
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
            if (!parameter.isContextual() || parameter.isContextualOverride()) {
                visible.add(parameter);
            }
        }

        return visible;
    }

    public static String paginationBar(
            ContentPage page,
            String command,
            Theme theme,
            DirectorTextResolver resolver
    ) {
        if (page == null || theme == null) {
            return "";
        }

        DirectorTextResolver activeResolver = resolver == null ? DirectorTextResolver.ENGLISH : resolver;
        return renderPaginationBar(
                page.hasPrevious(),
                page.hasNext(),
                page.page() - 1,
                page.page() + 1,
                page.previousCommand(command),
                page.nextCommand(command),
                theme,
                activeResolver
        );
    }

    public static ContentPage paginate(int itemCount, int requestedPage, int pageSize) {
        if (itemCount < 0) {
            throw new IllegalArgumentException("item count must not be negative");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("page size must be positive");
        }

        int totalPages = itemCount == 0 ? 1 : ((itemCount - 1) / pageSize) + 1;
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, itemCount);
        return new ContentPage(page, totalPages, startIndex, endIndex, itemCount);
    }

    public static ContentPage paginate(List<String> entries, int requestedPage, int pageSize, int reservedLines) {
        Objects.requireNonNull(entries, "entries");
        if (pageSize < 1) {
            throw new IllegalArgumentException("page size must be positive");
        }

        int rowBudget = Math.max(1, CHAT_LINE_COUNT - reservedLines);
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        int rows = 0;
        int count = 0;
        for (int index = 0; index < entries.size(); index++) {
            int entryRows = chatRows(entries.get(index));
            if (count > 0 && (count == pageSize || rows + entryRows > rowBudget)) {
                starts.add(index);
                rows = 0;
                count = 0;
            }
            rows += entryRows;
            count++;
        }

        int totalPages = starts.size();
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        int startIndex = starts.get(page - 1);
        int endIndex = page < totalPages ? starts.get(page) : entries.size();
        return new ContentPage(page, totalPages, startIndex, endIndex, entries.size());
    }

    public static int chatRows(String line) {
        if (line == null || line.trim().isEmpty()) {
            return 0;
        }

        ChatRowCounter counter = new ChatRowCounter();
        ComponentFlattener.basic().flatten(MINI_MESSAGE.deserialize(line), counter);
        return counter.rows();
    }

    private static int reservedLines(boolean backLink) {
        return backLink ? 3 : 2;
    }

    private static String renderPaginationBar(
            boolean hasPrevious,
            boolean hasNext,
            int previousPage,
            int nextPage,
            String previousCommand,
            String nextCommand,
            Theme theme,
            DirectorTextResolver resolver
    ) {
        StringBuilder line = new StringBuilder();
        String pageLabel = resolve(resolver, DirectorHelpMessages.PAGE);
        String previousLabel = "〈 " + pageLabel + " " + previousPage;
        String nextLabel = pageLabel + " " + nextPage + " ❭";
        int buttonWidth = (hasPrevious ? textWidth(previousLabel) + FONT_SPACE_WIDTH : 0)
                + (hasNext ? textWidth(nextLabel) + FONT_SPACE_WIDTH : 0);
        int fill = Math.max(1, (FOOTER_WIDTH * FONT_SPACE_WIDTH - buttonWidth) / FONT_SPACE_WIDTH);

        if (hasPrevious) {
            line.append("<hover:show_text:'")
                    .append(escapeAttr(escapeText(resolve(resolver, DirectorHelpMessages.PREVIOUS_PAGE))))
                    .append("'><click:run_command:")
                    .append(previousCommand)
                    .append("><").append(theme.primaryLeft()).append(">")
                    .append(escapeText(previousLabel))
                    .append("</").append(theme.primaryLeft()).append("></click></hover> ");
        }

        line.append("<font:minecraft:uniform><strikethrough><gradient:")
                .append(theme.borderRight()).append(":").append(theme.borderLeft()).append(">")
                .append(spaces(fill))
                .append("</gradient></strikethrough></font>");

        if (hasNext) {
            line.append(" <hover:show_text:'")
                    .append(escapeAttr(escapeText(resolve(resolver, DirectorHelpMessages.NEXT_PAGE))))
                    .append("'><click:run_command:")
                    .append(nextCommand)
                    .append("><").append(theme.primaryRight()).append(">")
                    .append(escapeText(nextLabel))
                    .append("</").append(theme.primaryRight()).append("></click></hover>");
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

    private static int textWidth(String value) {
        int width = 0;
        int index = 0;
        while (index < value.length()) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            width += glyphWidth(codePoint);
        }
        return width;
    }

    private static int characterWidth(char character) {
        return switch (character) {
            case '!', '\'', ',', '.', ':', ';', 'i', '|' -> 2;
            case '`', 'l' -> 3;
            case ' ', '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
            case '<', '>', 'f', 'k' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }

    private static int glyphWidth(int codePoint) {
        return codePoint < 128 ? characterWidth((char) codePoint) : WIDE_GLYPH_WIDTH;
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

    public record DirectorHelpPage(DirectorRuntimeNode node, List<DirectorRuntimeNode> entries, int pageIndex, int pageSize) {
        public DirectorHelpPage {
            Objects.requireNonNull(node, "node");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            pageIndex = Math.max(0, pageIndex);
            if (pageSize < 1) {
                throw new IllegalArgumentException("help page size must be positive");
            }
        }

        public DirectorHelpPage withEntries(List<DirectorRuntimeNode> visibleEntries) {
            return new DirectorHelpPage(node, visibleEntries, pageIndex, pageSize);
        }

        public String title() {
            return node.path();
        }

        public String pageCommand(int page) {
            return node.path() + " help=" + page;
        }

        public String parentCommand() {
            if (node.getParent() == null) {
                return node.path() + " help=1";
            }

            return node.getParent().path() + " help=1";
        }
    }

    public record ContentMenu(
            String title,
            String command,
            String parentCommand,
            List<String> entries,
            String emptyLine,
            int requestedPage,
            int pageSize
    ) {
        public ContentMenu(String title, String command, List<String> entries, String emptyLine,
                           int requestedPage, int pageSize) {
            this(title, command, "", entries, emptyLine, requestedPage, pageSize);
        }

        public ContentMenu {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("content menu title must not be blank");
            }
            if (command == null || command.isBlank()) {
                throw new IllegalArgumentException("content menu command must not be blank");
            }
            parentCommand = parentCommand == null ? "" : parentCommand;
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            emptyLine = emptyLine == null ? "" : emptyLine;
            if (pageSize < 1) {
                throw new IllegalArgumentException("content menu page size must be positive");
            }
        }

        public ContentPage page() {
            return paginate(entries, requestedPage, pageSize, reservedLines(!parentCommand.isBlank()));
        }
    }

    public record ContentPage(int page, int pages, int startIndex, int endIndex, int total) {
        public String title(String title) {
            if (pages <= 1) {
                return title;
            }

            return title + " {" + page + "/" + pages + "}";
        }

        public boolean hasPrevious() {
            return page > 1;
        }

        public boolean hasNext() {
            return page < pages;
        }

        public String previousCommand(String command) {
            return command + " page=" + (page - 1);
        }

        public String nextCommand(String command) {
            return command + " page=" + (page + 1);
        }
    }

    private static final class ChatRowCounter implements FlattenerListener {
        private final Deque<Boolean> bold = new ArrayDeque<>();
        private int rows = 1;
        private int rowWidth;
        private int wordWidth;
        private boolean breakable;

        @Override
        public void pushStyle(Style style) {
            TextDecoration.State state = style.decoration(TextDecoration.BOLD);
            boolean inherited = !bold.isEmpty() && bold.peek();
            bold.push(state == TextDecoration.State.NOT_SET ? inherited : state == TextDecoration.State.TRUE);
        }

        @Override
        public void component(String text) {
            boolean boldText = !bold.isEmpty() && bold.peek();
            int index = 0;
            while (index < text.length()) {
                int codePoint = text.codePointAt(index);
                index += Character.charCount(codePoint);
                append(codePoint, boldText);
            }
        }

        @Override
        public void popStyle(Style style) {
            bold.pop();
        }

        int rows() {
            return rows;
        }

        private void append(int codePoint, boolean boldText) {
            if (codePoint == '\n') {
                startRow(0);
                return;
            }

            int width = glyphWidth(codePoint) + (boldText ? 1 : 0);
            if (rowWidth > 0 && rowWidth + width > CHAT_WIDTH) {
                if (codePoint == ' ') {
                    startRow(0);
                    return;
                }
                startRow(breakable ? wordWidth : 0);
            }

            rowWidth += width;
            if (codePoint == ' ') {
                breakable = true;
                wordWidth = 0;
            } else {
                wordWidth += width;
            }
        }

        private void startRow(int carriedWidth) {
            rows++;
            rowWidth = carriedWidth;
            wordWidth = carriedWidth;
            breakable = false;
        }
    }
}
