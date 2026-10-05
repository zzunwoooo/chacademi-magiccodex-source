package dev.portablevfx.client;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.portablevfx.client.api.PortableVfxApi;
import dev.portablevfx.client.render.EffectRenderer;
import dev.portablevfx.client.definition.EffectLibrary;
import dev.portablevfx.protocol.PlayEffect;
import dev.portablevfx.protocol.VfxProtocol;
import java.util.UUID;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Explicitly debug-only local previews; normal spell selection is provided by the server. */
final class ClientCommands {
    private static final DynamicCommandExceptionType INVALID = new DynamicCommandExceptionType(
            detail -> Text.literal("PortableVFX: " + detail));

    private ClientCommands() { }

    static void register(PortableVfxApi api, EffectLibrary library, EffectRenderer renderer, dev.portablevfx.client.claude.ClaudePreviewController claude) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
                dispatcher.register(debugRoot(api, library, renderer, claude)));
    }

    static LiteralArgumentBuilder<FabricClientCommandSource> debugRoot(PortableVfxApi api, EffectLibrary library,
            EffectRenderer renderer, dev.portablevfx.client.claude.ClaudePreviewController claude) {
            var root = literal("pvfxdebug").executes(context -> {
                context.getSource().sendFeedback(Text.literal("/pvfxdebug play <id> [scale] [ticks] [yaw] [pitch] [roll] | playproof <id> ... (seed 0) | playhere <id> ... | playat <id> <x> <y> <z> ... | follow <id> | groundlaunch <id> <endId> <width> <speed> <distance> <holdSeconds> | burst <uuid> <id> | launch <claude-id> [speed] | impact <uuid> <id> [normalX normalY normalZ] | finish <uuid> | configerrors | status | list | active | stop <uuid> | clear | reload"));
                return 1;
            });
            root.then(literal("configerrors").executes(c -> {
                c.getSource().sendFeedback(Text.literal(library.configErrors().isEmpty()?"Claude config: no errors":String.join("\n",library.configErrors())));return 1;
            }));
            root.then(literal("launch").then(effectArgument(api).executes(c -> launch(c,claude,10))
                .then(argument("speed", DoubleArgumentType.doubleArg(0,32)).executes(c -> launch(c,claude,DoubleArgumentType.getDouble(c,"speed"))))));
            root.then(literal("follow").then(effectArgument(api).executes(c -> {
                UUID id=claude.follow(c.getArgument("effect",Identifier.class)).orElseThrow(()->INVALID.create("Entity-foot follow rejected"));
                c.getSource().sendFeedback(Text.literal("Client-only attached preview: "+id));return 1;
            })));
            root.then(literal("groundlaunch").then(effectArgument(api)
                .then(argument("endEffect",IdentifierArgumentType.identifier())
                .then(argument("width",DoubleArgumentType.doubleArg(.01,64))
                .then(argument("speed",DoubleArgumentType.doubleArg(.01,32))
                .then(argument("distance",DoubleArgumentType.doubleArg(.01,128))
                .then(argument("hold",DoubleArgumentType.doubleArg(0,10)).executes(c -> {
                    UUID id=claude.groundLaunch(c.getArgument("effect",Identifier.class),c.getArgument("endEffect",Identifier.class),
                        DoubleArgumentType.getDouble(c,"width"),DoubleArgumentType.getDouble(c,"speed"),
                        DoubleArgumentType.getDouble(c,"distance"),DoubleArgumentType.getDouble(c,"hold"))
                        .orElseThrow(()->INVALID.create("Ground preview rejected; hold+distance/speed must be <=19 seconds"));
                    c.getSource().sendFeedback(Text.literal("Client-only horizontal preview: "+id+"; no collision, terrain or gameplay; burst/finish/stop supported"));return 1;
                }))))))));
            root.then(literal("burst").then(argument("instance",StringArgumentType.word()).then(effectArgument(api).executes(c -> {
                try {
                    UUID id=claude.burst(UUID.fromString(StringArgumentType.getString(c,"instance")),c.getArgument("effect",Identifier.class))
                        .orElseThrow(()->INVALID.create("Active preview and valid burst effect required"));
                    c.getSource().sendFeedback(Text.literal("Independent impact at your feet: "+id));return 1;
                }catch(IllegalArgumentException e){throw INVALID.create(e.getMessage());}
            }))));
            root.then(literal("finish").then(argument("instance",StringArgumentType.word()).executes(c -> {
                try { boolean ok=api.finish(UUID.fromString(StringArgumentType.getString(c,"instance")));
                    c.getSource().sendFeedback(Text.literal(ok?"Claude emission stopped; detached particles drain":"Claude instance not found"));return ok?1:0;
                } catch(IllegalArgumentException e){throw INVALID.create(e.getMessage());}
            })));
            root.then(literal("impact").then(argument("instance",StringArgumentType.word()).then(effectArgument(api)
                .executes(c -> impact(c,claude,null))
                .then(argument("normalX",DoubleArgumentType.doubleArg(-1,1))
                .then(argument("normalY",DoubleArgumentType.doubleArg(-1,1))
                .then(argument("normalZ",DoubleArgumentType.doubleArg(-1,1)).executes(c -> impact(c,claude,new Vec3d(
                    DoubleArgumentType.getDouble(c,"normalX"),DoubleArgumentType.getDouble(c,"normalY"),DoubleArgumentType.getDouble(c,"normalZ"))))))))));
            root.then(literal("status").executes(context -> {
                context.getSource().sendFeedback(Text.literal(api.status() + " / worldFrames=" + renderer.worldFrames() + " / active=" + api.activeInstances().size() + " / " + renderer.prewarmStatus()));
                context.getSource().sendFeedback(Text.literal("[VFX 진단] "+renderer.readinessDiagnostic()+" / "+dev.portablevfx.client.network.VfxNetworking.diagnostic()));return 1;
            }));
            root.then(literal("list").executes(context -> {
                context.getSource().sendFeedback(Text.literal("Effects (" + api.list().size() + "): " +
                        String.join(", ", api.list().stream().map(Object::toString).sorted().toList())));
                return api.list().size();
            }));
            root.then(literal("active").executes(context -> {
                context.getSource().sendFeedback(Text.literal("Active (" + api.activeInstances().size() + "): " +
                        String.join(", ", api.activeInstances().stream().map(Object::toString).sorted().toList())));
                return api.activeInstances().size();
            }));
            root.then(literal("clear").executes(context -> {
                api.clear(); context.getSource().sendFeedback(Text.literal("PortableVFX: cleared")); return 1;
            }));
            root.then(literal("reload").executes(context -> {
                api.clear();
                context.getSource().getClient().reloadResources();
                context.getSource().sendFeedback(Text.literal("PortableVFX: resource reload requested (same resources as F3+T)"));
                return 1;
            }));
            root.then(literal("stop").then(argument("instance", StringArgumentType.word())
                    .suggests((context, builder) -> CommandSource.suggestMatching(api.activeInstances().stream().map(Object::toString), builder))
                    .executes(context -> {
                        try {
                            boolean stopped = api.stop(UUID.fromString(StringArgumentType.getString(context, "instance")));
                            context.getSource().sendFeedback(Text.literal(stopped ? "PortableVFX: stopped" : "PortableVFX: instance not found"));
                            return stopped ? 1 : 0;
                        } catch (IllegalArgumentException ex) { throw INVALID.create("invalid UUID"); }
                    })));
            root.then(literal("play").then(effectArgument(api).executes(context -> play(context, api, library, 0))
                    .then(options(api, library, 0))));
            root.then(literal("playproof").then(effectArgument(api).executes(c -> play(c, api, library, 3)).then(options(api, library, 3))));
            root.then(literal("playhere").then(effectArgument(api).executes(c -> play(c, api, library, 2)).then(options(api, library, 2))));
            root.then(literal("playat").then(effectArgument(api)
                    .then(argument("x", DoubleArgumentType.doubleArg(-VfxProtocol.MAX_POSITION, VfxProtocol.MAX_POSITION))
                    .then(argument("y", DoubleArgumentType.doubleArg(-VfxProtocol.MAX_POSITION, VfxProtocol.MAX_POSITION))
                    .then(argument("z", DoubleArgumentType.doubleArg(-VfxProtocol.MAX_POSITION, VfxProtocol.MAX_POSITION))
                            .executes(context -> play(context, api, library, 1)).then(options(api, library, 1)))))));
            return root;
    }

    private static int launch(CommandContext<FabricClientCommandSource> c,dev.portablevfx.client.claude.ClaudePreviewController claude,double speed)throws CommandSyntaxException {
        UUID id=claude.launch(c.getArgument("effect",Identifier.class),speed).orElseThrow(()->INVALID.create("Claude launch not accepted"));
        c.getSource().sendFeedback(Text.literal("Claude cosmetic flight: "+id+" (use impact, finish, or stop)"));return 1;
    }
    private static int impact(CommandContext<FabricClientCommandSource> c,dev.portablevfx.client.claude.ClaudePreviewController claude,Vec3d normal)throws CommandSyntaxException {
        try {
            UUID id=claude.impact(UUID.fromString(StringArgumentType.getString(c,"instance")),c.getArgument("effect",Identifier.class),normal)
                .orElseThrow(()->INVALID.create("Active launched Claude instance and valid impact ID required"));
            c.getSource().sendFeedback(Text.literal("Claude impact: "+id));return 1;
        } catch(IllegalArgumentException e){throw INVALID.create(e.getMessage());}
    }

    private static RequiredArgumentBuilder<FabricClientCommandSource, Identifier> effectArgument(PortableVfxApi api) {
        return argument("effect", IdentifierArgumentType.identifier()).suggests((context, builder) ->
                CommandSource.suggestMatching(api.list().stream().map(Object::toString), builder));
    }

    private static RequiredArgumentBuilder<FabricClientCommandSource, Float> options(PortableVfxApi api, EffectLibrary library, int placement) {
        return argument("scale", FloatArgumentType.floatArg(.01f, VfxProtocol.MAX_SCALE))
                .executes(c -> play(c, api, library, placement))
                .then(argument("duration", IntegerArgumentType.integer(1, VfxProtocol.MAX_DURATION_TICKS))
                        .executes(c -> play(c, api, library, placement))
                .then(argument("yaw", FloatArgumentType.floatArg(-360, 360)).executes(c -> play(c, api, library, placement))
                .then(argument("pitch", FloatArgumentType.floatArg(-360, 360)).executes(c -> play(c, api, library, placement))
                .then(argument("roll", FloatArgumentType.floatArg(-360, 360)).executes(c -> play(c, api, library, placement))))));
    }

    private static int play(CommandContext<FabricClientCommandSource> context, PortableVfxApi api,
            EffectLibrary library, int placement) throws CommandSyntaxException {
        try {
            var source = context.getSource();
            Identifier id = context.getArgument("effect", Identifier.class);
            var effect = library.get(id);
            if (effect == null) throw INVALID.create("unknown effect ID: " + id);
            Vec3d position;
            if (placement == 1) position = new Vec3d(DoubleArgumentType.getDouble(context, "x"),
                    DoubleArgumentType.getDouble(context, "y"), DoubleArgumentType.getDouble(context, "z"));
            else if (placement == 2) position = source.getPlayer().getPos().add(0, .02, 0);
            else {
                Vec3d look = source.getPlayer().getRotationVec(1);
                position = source.getPlayer().getPos().add(look.x * 2, 1, look.z * 2);
            }
            UUID instance = UUID.randomUUID();
            String rgbString = optional(context, "rgb", String.class, "ffffff");
            if (!rgbString.matches("[a-fA-F0-9]{6}")) throw INVALID.create("RGB must be six hex digits RRGGBB (without #)");
            int rgb = Integer.parseInt(rgbString, 16);
            PlayEffect request = new PlayEffect(instance, id.toString(), source.getWorld().getRegistryKey().getValue().toString(),
                    position.x, position.y, position.z,
                    optional(context, "yaw", Float.class, 0f), optional(context, "pitch", Float.class, 0f),
                    optional(context, "roll", Float.class, 0f), optional(context, "scale", Float.class, 1f), rgb,
                    optional(context, "opacity", Float.class, 1f), optional(context, "duration", Integer.class, effect.durationTicks()));
            if (placement == 3) request = new PlayEffect(instance, request.effectId(), request.dimensionId(),
                    position.x, position.y, position.z, request.yaw(), request.pitch(), request.roll(), request.scale(),
                    request.rgb(), request.opacity(), request.durationTicks(), null, 0, -1,
                    dev.portablevfx.protocol.EffectAnchor.WORLD, 0, 0, 0);
            if (!api.play(request)) throw INVALID.create("not accepted: unknown ID, unsupported tint/opacity/Claude scale (must be 1), wrong world, >256 blocks, or capacity");
            source.sendFeedback(Text.literal("PortableVFX accepted (check /pvfxdebug status): " + id + " → " + instance));
            return 1;
        } catch (IllegalArgumentException ex) { throw INVALID.create(ex.getMessage()); }
    }

    private static <T> T optional(CommandContext<?> context, String name, Class<T> type, T fallback) {
        try { return context.getArgument(name, type); }
        catch (IllegalArgumentException missing) { return fallback; }
    }
}
