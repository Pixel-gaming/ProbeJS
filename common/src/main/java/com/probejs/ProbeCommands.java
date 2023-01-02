package com.probejs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonWriter;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.probejs.compiler.DocCompiler;
import com.probejs.compiler.SchemaCompiler;
import com.probejs.compiler.SnippetCompiler;
import com.probejs.formatter.ClassResolver;
import com.probejs.formatter.NameResolver;
import com.probejs.formatter.formatter.jdoc.FormatterClass;
import com.probejs.info.ClassInfo;
import com.probejs.jdoc.document.DocumentClass;
import dev.latvian.mods.kubejs.KubeJSPaths;
import dev.latvian.mods.kubejs.item.ingredient.IngredientJS;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.SharedContextData;
import dev.latvian.mods.rhino.mod.util.RemappingHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class ProbeCommands {
    public static Context CONTEXT;
    public static Scriptable SCOPE;
    public static SharedContextData CONTEXT_DATA;
    //Leaking access here since I don't want to think hard...
    public static ServerLevel COMMAND_LEVEL;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("probejs")
                        .then(Commands.literal("dump")
                                //SINGLE PLAYER IS NEEDED
                                .requires(source -> source.getServer().isSingleplayer() && source.hasPermission(2))
                                .executes(context -> {
                                    COMMAND_LEVEL = context.getSource().getLevel();
                                    Instant start = Instant.now();
                                    try {
                                        if (CONTEXT == null) {
                                            CONTEXT = Context.enterWithNewFactory();
                                            SCOPE = CONTEXT.initStandardObjects();
                                            CONTEXT_DATA = SharedContextData.get(SCOPE);
                                            CONTEXT_DATA.setExtraProperty("Type", ScriptType.SERVER);
                                            CONTEXT_DATA.setExtraProperty("Console", ScriptType.SERVER.console);
                                            CONTEXT_DATA.setRemapper(RemappingHelper.getMinecraftRemapper());
                                        }
                                        SnippetCompiler.compile();
                                        ClassResolver.init();
                                        NameResolver.init();
                                        DocCompiler.compile();
                                    } catch (Exception e) {
                                        ProbeJS.LOGGER.error(e.getMessage());
                                        for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                                            ProbeJS.LOGGER.error(stackTraceElement);
                                        }
                                        context.getSource().sendSuccess(new TextComponent("Uncaught exception happened in wrapper, please report to the Github issue with complete latest.log."), false);
                                    }
                                    Instant end = Instant.now();
                                    Duration duration = Duration.between(start, end);
                                    long sub = TimeUnit.MILLISECONDS.convert(duration.getNano(), TimeUnit.NANOSECONDS);
                                    context.getSource().sendSuccess(new TextComponent("ProbeJS typing generation finished in %s.%03ds.".formatted(duration.getSeconds(), sub)), false);
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("clear_cache")
                                .requires(source -> source.getServer().isSingleplayer())
                                .executes(context -> {
                                    for (File file : Objects.requireNonNull(ProbePaths.CACHE.toFile().listFiles())) {
                                        if (file.isFile()) {
                                            if (file.delete()) {
                                                ProbeJS.LOGGER.info("Removed %s".formatted(file.getName()));
                                            } else {
                                                ProbeJS.LOGGER.info("Failed to remove %s".formatted(file.getName()));
                                            }
                                        }
                                    }
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("configure")
                                .requires(source -> source.getServer().isSingleplayer())
                                .then(Commands.literal("toggle_bean")
                                        .executes(context -> {
                                            ProbeConfig.INSTANCE.dumpMethod = !ProbeConfig.INSTANCE.dumpMethod;
                                            context.getSource().sendSuccess(new TextComponent("Keep method while beaning set to: %s".formatted(ProbeConfig.INSTANCE.dumpMethod)), false);
                                            ProbeConfig.INSTANCE.save();
                                            return Command.SINGLE_SUCCESS;
                                        }))
                                .then(Commands.literal("toggle_aggressive")
                                        .executes(context -> {
                                            ProbeConfig.INSTANCE.noAggressiveProbing = !ProbeConfig.INSTANCE.noAggressiveProbing;
                                            context.getSource().sendSuccess(new TextComponent("Aggressive mode is now: %s".formatted(ProbeConfig.INSTANCE.noAggressiveProbing ? "disabled" : "enabled")), false);
                                            ProbeConfig.INSTANCE.save();
                                            context.getSource().sendSuccess(new TextComponent("Changes will be applied next time you start the game."), false);
                                            return Command.SINGLE_SUCCESS;
                                        }))
                                .then(Commands.literal("toggle_snippet_order")
                                        .executes(context -> {
                                            ProbeConfig.INSTANCE.vanillaOrder = !ProbeConfig.INSTANCE.vanillaOrder;
                                            context.getSource().sendSuccess(new TextComponent("In snippets, which will appear first: %s".formatted(ProbeConfig.INSTANCE.vanillaOrder ? "mod_id" : "member_type")), false);
                                            ProbeConfig.INSTANCE.save();
                                            return Command.SINGLE_SUCCESS;
                                        }))
                                .then(Commands.literal("toggle_classname_snippets")
                                        .executes(context -> {
                                            ProbeConfig.INSTANCE.exportClassNames = !ProbeConfig.INSTANCE.exportClassNames;
                                            context.getSource().sendSuccess(new TextComponent("Export class name as snippets set to: %s".formatted(ProbeConfig.INSTANCE.exportClassNames)), false);
                                            ProbeConfig.INSTANCE.save();
                                            return Command.SINGLE_SUCCESS;
                                        }))
                        )
                        .then(Commands.literal("export")
                                .requires(source -> source.getServer().isSingleplayer())
                                .then(Commands.argument("className", StringArgumentType.string())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                ClassInfo.CLASS_CACHE
                                                        .values()
                                                        .stream()
                                                        .map(ClassInfo::getName),
                                                builder)
                                        )
                                        .executes(ctx -> {
                                            String className = StringArgumentType.getString(ctx, "className");
                                            ClassInfo info = ClassInfo.CLASS_NAME_CACHE.get(className);
                                            String[] nameParts = info.getName().split("\\.");
                                            JsonObject document = DocumentClass.fromJava(info).serialize();
                                            JsonArray outArray = new JsonArray();
                                            outArray.add(document);
                                            try {
                                                BufferedWriter writer = Files.newBufferedWriter(KubeJSPaths.EXPORTED.resolve(nameParts[nameParts.length - 1] + ".json"));
                                                JsonWriter jsonWriter = ProbeJS.GSON_WRITER.newJsonWriter(writer);
                                                jsonWriter.setIndent("    ");
                                                ProbeJS.GSON_WRITER.toJson(outArray, JsonArray.class, jsonWriter);
                                                jsonWriter.flush();
                                            } catch (IOException e) {
                                                throw new RuntimeException(e);
                                            }
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("test")
                                .requires(source -> true)
                                .executes(context -> {
                                    try {
                                        DocumentClass document = DocumentClass.fromJava(ClassInfo.getOrCache(IngredientJS.class));
                                        ProbeJS.LOGGER.info(document.isAbstract());
                                        ProbeJS.LOGGER.info(ProbeJS.GSON.toJson(document.serialize()));
                                        ProbeJS.LOGGER.info(String.join("\n", new FormatterClass(document).format(0, 4)));
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    return Command.SINGLE_SUCCESS;
                                }))
        );
    }

}
