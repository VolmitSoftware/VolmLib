package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NativeCommandArguments {
    private NativeCommandArguments() {
    }

    public static ArgumentType<?> identifier() {
        return IdentifierArgument.id();
    }

    public static String getIdentifier(CommandContext<NativeCommandSource> context, String name) {
        return IdentifierArgument.getId(nativeContext(context), name).toString();
    }

    public static ArgumentType<?> dimension() {
        return DimensionArgument.dimension();
    }

    public static ArgumentType<?> player() {
        return EntityArgument.player();
    }

    public static NativeWorld getDimension(CommandContext<NativeCommandSource> context, String name)
            throws CommandSyntaxException {
        return new ModdedPlatformWorld(DimensionArgument.getDimension(nativeContext(context), name));
    }

    public static NativeProtocolPlayer getPlayer(CommandContext<NativeCommandSource> context, String name)
            throws CommandSyntaxException {
        return NativeProtocolPlayer.fromHandle(EntityArgument.getPlayer(nativeContext(context), name));
    }

    private static CommandContext<CommandSourceStack> nativeContext(CommandContext<NativeCommandSource> context) {
        Map<String, ParsedArgument<CommandSourceStack, ?>> arguments = new HashMap<>();
        for (ParsedCommandNode<NativeCommandSource> node : context.getNodes()) {
            if (node.getNode() instanceof ArgumentCommandNode<NativeCommandSource, ?>) {
                String name = node.getNode().getName();
                arguments.put(name, new ParsedArgument<>(node.getRange().getStart(), node.getRange().getEnd(),
                        context.getArgument(name, Object.class)));
            }
        }
        return new CommandContext<>(context.getSource().source(), context.getInput(), arguments, null,
                new RootCommandNode<>(), List.of(), context.getRange(), null, null, false);
    }
}
