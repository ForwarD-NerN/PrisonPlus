package ru.nern.prisonplus.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import ru.nern.prisonplus.ConfigurationManager;
import ru.nern.prisonplus.PrisonNetworking;
import ru.nern.prisonplus.PrisonPlus;
import ru.nern.prisonplus.structure.Prison;
import ru.nern.prisonplus.structure.PrisonCell;
import ru.nern.prisonplus.structure.PrisonItems;
import ru.nern.prisonplus.structure.enums.AllowedInteraction;
import ru.nern.prisonplus.structure.enums.PlayerGroup;
import ru.nern.prisonplus.utils.ILivingEntityAccessor;
import ru.nern.prisonplus.utils.IPlayerAccessor;
import ru.nern.prisonplus.utils.IWorldPrisonAccessor;
import ru.nern.prisonplus.utils.PrisonUtils;

import javax.annotation.Nullable;
import java.util.Iterator;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class PrisonCommands {
    public static final SimpleCommandExceptionType PRISON_INTERSECTION_ERROR = new SimpleCommandExceptionType(Text.literal("Тюрьмы не должны пересекаться"));
    public static final SimpleCommandExceptionType CELL_INTERSECTION_ERROR = new SimpleCommandExceptionType(Text.literal("Клетки не должны пересекаться"));
    public static final SimpleCommandExceptionType CELL_OUTSIDE_ERROR = new SimpleCommandExceptionType(Text.literal("Клетки должны быть в зоне тюрьмы"));
    public static final SimpleCommandExceptionType DUPLICATE_ERROR = new SimpleCommandExceptionType(Text.literal("Элемент с таким названием уже существует"));
    public static final SimpleCommandExceptionType PRISON_NOT_FOUND_ERROR = new SimpleCommandExceptionType(Text.literal("Тюрьма с таким названием не найдена"));
    public static final SimpleCommandExceptionType PRISON_IN_ANOTHER_DIMENSION = new SimpleCommandExceptionType(Text.literal("Тюрьма находится в другом измерении"));
    public static final SimpleCommandExceptionType PRISON_OVERFILLED = new SimpleCommandExceptionType(Text.literal("В тюрьме заполнены все клетки"));
    public static final SimpleCommandExceptionType CELL_NOT_FOUND_ERROR = new SimpleCommandExceptionType(Text.literal("Клетка с таким названием не найдена"));
    public static final SimpleCommandExceptionType PLAYER_REQUIRED = new SimpleCommandExceptionType(Text.literal("Необходимо ввести имя игрока"));
    public static final SimpleCommandExceptionType PLAYER_ISNT_IN_PRISON = new SimpleCommandExceptionType(Text.literal("Игрок не находится в тюрьме"));
    public static final SimpleCommandExceptionType PLAYER_IS_IN_PRISON = new SimpleCommandExceptionType(Text.literal("Игрок уже находится в тюрьме"));
    public static final SimpleCommandExceptionType WRONG_DIRECTION_ERROR = new SimpleCommandExceptionType(Text.literal("Направление не найдено"));
    public static final SimpleCommandExceptionType WRONG_TYPE = new SimpleCommandExceptionType(Text.literal("Тип времени заключения указан не правильно. Должен быть irl или igt."));
    public static final SimpleCommandExceptionType WRONG_POLICY = new SimpleCommandExceptionType(Text.literal("Такой политики взаимодействия не существует"));
    public static final SimpleCommandExceptionType INVALID_TIME = new SimpleCommandExceptionType(Text.literal("Время указано не правильно"));
    public static final SimpleCommandExceptionType IN_RANGE_REQUIRED = new SimpleCommandExceptionType(Text.literal("Игрок находится слишком далеко от тюрьмы"));
    public static final SimpleCommandExceptionType HANDCUFFS_REQUIRED = new SimpleCommandExceptionType(Text.literal("Необходимо надеть на игрока наручники"));
    public static final SimpleCommandExceptionType ENTITY_CANT_BE_LEASHED = new SimpleCommandExceptionType(Text.literal("Эта сущность не может быть взята в наручники"));
    public static final SimpleCommandExceptionType INVALID_ENTITY = new SimpleCommandExceptionType(Text.literal("Сущность не указана"));
    public static final SimpleCommandExceptionType ENTITY_IS_FREE = new SimpleCommandExceptionType(Text.literal("Эта сущность не находится в наручниках"));

    private static final SuggestionProvider<ServerCommandSource> PRISON_SUGGESTION_PROVIDER = (context, builder) -> {
        ServerWorld world;
        try{
            world = DimensionArgumentType.getDimensionArgument(context, "dimension");
        }catch (IllegalArgumentException e) {
            world = context.getSource().getWorld();
        }

        IWorldPrisonAccessor accessor = (IWorldPrisonAccessor) world;
        Iterator<Prison> iterator = accessor.getPrisonIterator();
        while (iterator.hasNext())
        {
            String prisonName = iterator.next().getName();
            if(CommandSource.shouldSuggest(builder.getRemaining(), prisonName)) builder.suggest(prisonName);
        }

        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> CELL_SUGGESTION_PROVIDER = (context, builder) -> {
        ServerWorld world;
        String prisonName = context.getArgument("prisonName", String.class);

        try{
            world = DimensionArgumentType.getDimensionArgument(context, "dimension");
        }catch (IllegalArgumentException e) {
            world = context.getSource().getWorld();
        }
        Prison prison = PrisonUtils.getPrisonOrThrow(world, prisonName);

        for (PrisonCell cell : prison.getCells().values()) {
            String cellName = cell.getName();
            if(CommandSource.shouldSuggest(builder.getRemaining(), cellName)) builder.suggest(cellName);
        }

        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> PLAYER_GROUP_SUGGESTION_PROVIDER = (context, builder) -> {
        for(PlayerGroup policy : PlayerGroup.values()) {
            String name = policy.name().toLowerCase();
            if(CommandSource.shouldSuggest(builder.getRemaining(), name)) builder.suggest(name);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> INTERACTION_SUGGESTION_PROVIDER = (context, builder) -> {
        for(AllowedInteraction policy : AllowedInteraction.values()) {
            String name = policy.name().toLowerCase();
            if(CommandSource.shouldSuggest(builder.getRemaining(), name)) builder.suggest(name);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> TIME_SUGGESTION_PROVIDER = (context, builder) -> {
        StringReader stringReader = new StringReader(builder.getRemaining());
        try {
            stringReader.readFloat();
        }
        catch (CommandSyntaxException commandSyntaxException) {
            return builder.buildFuture();
        }
        return CommandSource.suggestMatching(PrisonUtils.TIME_UNITS.keySet(), builder.createOffset(builder.getStart() + stringReader.getCursor()));
    };

    private static final SuggestionProvider<ServerCommandSource> CUSTOM_ITEMS = (context, builder) -> {
        return builder.suggest("handcuffs").suggest("baton").suggest("scissors").buildFuture();
    };

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("jail").requires(Permissions.require("prisonplus.jail", 2))//.requires(source -> source.hasPermissionLevel(2))
                    .then(argument("player", EntityArgumentType.player())
                            .then(argument("time", StringArgumentType.string()).suggests(TIME_SUGGESTION_PROVIDER)
                                    .then(argument("type", StringArgumentType.string())
                                            .suggests((context, builder) -> builder.suggest("irl").suggest("igt").buildFuture())
                                            .then(argument("reason", StringArgumentType.string())
                                                    .then(argument("prisonName", StringArgumentType.string()).suggests(PRISON_SUGGESTION_PROVIDER)
                                                            .executes((ctx) -> jailPlayer(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "time"), StringArgumentType.getString(ctx, "type"), StringArgumentType.getString(ctx, "prisonName"), null, null, StringArgumentType.getString(ctx, "reason")))
                                                            .then(argument("cellName", StringArgumentType.string()).suggests(CELL_SUGGESTION_PROVIDER)
                                                                    .executes((ctx) -> jailPlayer(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "time"), StringArgumentType.getString(ctx, "type"), StringArgumentType.getString(ctx, "prisonName"), null, StringArgumentType.getString(ctx, "cellName"), StringArgumentType.getString(ctx, "reason"))))
                                                            .then(argument("dimension", DimensionArgumentType.dimension())
                                                                    .executes((ctx) -> jailPlayer(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "time"), StringArgumentType.getString(ctx, "type"), StringArgumentType.getString(ctx, "prisonName"), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), null, StringArgumentType.getString(ctx, "reason")))
                                                                    .then(argument("cellName", StringArgumentType.string())
                                                                            .suggests(CELL_SUGGESTION_PROVIDER)
                                                                            .executes((ctx) -> jailPlayer(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "time"), StringArgumentType.getString(ctx, "type"), StringArgumentType.getString(ctx, "prisonName"), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "cellName"), StringArgumentType.getString(ctx, "reason")))))))))));
            dispatcher.register(literal("release").requires(Permissions.require("prisonplus.release", 2))
                    .then(argument("player", EntityArgumentType.player()).executes((ctx -> releasePlayer(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"))))));
            dispatcher.register(literal("prisonplus")
                    .then(literal("reload").requires(Permissions.require("prisonplus.reload", 2)).executes((ctx) -> reloadConfig(ctx.getSource())))
                            .then(literal("free").requires(Permissions.require("prisonplus.free", 2))
                                    .then(argument("entity", EntityArgumentType.entity()).executes(ctx -> takeoffHandcuffs(ctx.getSource(), EntityArgumentType.getEntity(ctx, "entity"))))
                                    .executes(ctx -> takeoffHandcuffs(ctx.getSource(), null)))
                    .then(literal("get").requires(Permissions.require("prisonplus.get_items", 2))
                            .then(argument("name", StringArgumentType.string())
                                    .suggests(CUSTOM_ITEMS)
                                    .executes((ctx) ->
                                            getItem(ctx.getSource(), StringArgumentType.getString(ctx, "name"), null))
                                    .then(argument("player", EntityArgumentType.player()).executes((ctx) -> getItem(ctx.getSource(),  StringArgumentType.getString(ctx, "name"), EntityArgumentType.getPlayer(ctx, "player"))))))
                    .then(literal("info")
                            .executes(ctx -> printModInfo(ctx.getSource())).requires(Permissions.require("prisonplus.info", 0))
                            .then(argument("prisoner", EntityArgumentType.player()).requires(Permissions.require("prisonplus.info.prisoner", 2))
                                    .executes(ctx -> printPrisonerInfo(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "prisoner")))))
                    .then(literal("list").requires(Permissions.require("prisonplus.list", 2))
                            .executes((ctx) -> printAllPrisons(ctx.getSource(), null))
                            .then(argument("dimension", DimensionArgumentType.dimension())
                                    .executes((ctx) -> printAllPrisons(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension")))))
                    .then(literal("remove").requires(Permissions.require("prisonplus.configure", 2))
                            .then(argument("prisonName", StringArgumentType.string()).suggests(PRISON_SUGGESTION_PROVIDER)
                                    .executes((ctx) -> removePrison(ctx.getSource(), StringArgumentType.getString(ctx, "prisonName"), null)).then(argument("dimension", DimensionArgumentType.dimension()).executes((ctx) -> removePrison(ctx.getSource(), StringArgumentType.getString(ctx, "prisonName"), DimensionArgumentType.getDimensionArgument(ctx, "dimension"))))))
                    .then(literal("subscribe").requires(Permissions.require("prisonplus.subscribe", 2))
                            .then(argument("prisonName", StringArgumentType.string()).suggests(PRISON_SUGGESTION_PROVIDER)
                                    .executes((ctx) -> subscribe(ctx.getSource(), StringArgumentType.getString(ctx, "prisonName"), null, null))
                                    .then(argument("dimension", DimensionArgumentType.dimension())
                                            .executes((ctx) -> subscribe(ctx.getSource(), StringArgumentType.getString(ctx, "prisonName"), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), null))
                                            .then(argument("player", EntityArgumentType.player())
                                                    .executes((ctx) -> subscribe(ctx.getSource(), StringArgumentType.getString(ctx, "prisonName"), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), EntityArgumentType.getPlayer(ctx, "player")))))))
                    .then(literal("create").requires(Permissions.require("prisonplus.configure", 2))
                            .then(argument("startPoint", BlockPosArgumentType.blockPos())
                                    .then(argument("endPoint", BlockPosArgumentType.blockPos())
                                            .then(argument("prisonName", StringArgumentType.string()).executes((ctx) -> newPrison(ctx.getSource(), BlockBox.create(BlockPosArgumentType.getLoadedBlockPos(ctx, "startPoint"), BlockPosArgumentType.getLoadedBlockPos(ctx, "endPoint")), StringArgumentType.getString(ctx, "prisonName"), null))
                                                    .then(argument("dimension", DimensionArgumentType.dimension())
                                                            .executes((ctx) -> newPrison(ctx.getSource(), BlockBox.create(BlockPosArgumentType.getLoadedBlockPos(ctx, "startPoint"), BlockPosArgumentType.getLoadedBlockPos(ctx, "endPoint")), StringArgumentType.getString(ctx, "prisonName"), DimensionArgumentType.getDimensionArgument(ctx, "dimension"))))))))
                    .then(literal("tp").requires(Permissions.require("prisonplus.teleport", 2))
                            .then(argument("prisonName", StringArgumentType.string())
                                    .suggests(PRISON_SUGGESTION_PROVIDER)
                                    .executes(ctx -> teleportToPrison(ctx.getSource(), StringArgumentType.getString(ctx, "prisonName"), null))
                                    .then(argument("player", EntityArgumentType.player())
                                            .executes(ctx -> teleportToPrison(ctx.getSource(), StringArgumentType.getString(ctx, "prisonName"), EntityArgumentType.getPlayer(ctx, "player"))))))
                    .then(literal("configure").requires(Permissions.require("prisonplus.configure", 2)).then(argument("prisonName", StringArgumentType.string()).suggests(PRISON_SUGGESTION_PROVIDER).then(argument("dimension", DimensionArgumentType.dimension())
                            .then(literal("list").executes((ctx) -> printAllCells(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"))))
                            .then(literal("set")
                                    .then(literal("policy")
                                            .then(argument("group", StringArgumentType.string())
                                                    .suggests(PLAYER_GROUP_SUGGESTION_PROVIDER)
                                                    .then(argument("interaction", StringArgumentType.string())
                                                            .suggests(INTERACTION_SUGGESTION_PROVIDER).then(argument("allow", BoolArgumentType.bool())
                                                            .executes((ctx) -> setPolicy(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"), StringArgumentType.getString(ctx, "group"), StringArgumentType.getString(ctx, "interaction"), BoolArgumentType.getBool(ctx, "allow")))))))
                                    .then(literal("canExitCells").then(argument("can", BoolArgumentType.bool()).executes((ctx) -> setCanExitCells(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"), BoolArgumentType.getBool(ctx, "can")))))
                                    .then(literal("cellPlayerLimit").then(argument("cellName", StringArgumentType.string()).suggests(CELL_SUGGESTION_PROVIDER).then(argument("amount", IntegerArgumentType.integer()).executes((ctx) -> setMaxPlayerCountInCell(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"), StringArgumentType.getString(ctx, "cellName"), IntegerArgumentType.getInteger(ctx, "amount")))))))
                            .then(literal("remove")
                                    .then(argument("cellName", StringArgumentType.string()).suggests(CELL_SUGGESTION_PROVIDER).executes((ctx) -> removeCell(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"), StringArgumentType.getString(ctx, "cellName")))))
                            .then(literal("move")
                                    .then(literal("prison")
                                            .then(argument("offset", IntegerArgumentType.integer())
                                                    .executes((ctx) -> movePrisonBoundaries(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"), null, IntegerArgumentType.getInteger(ctx,"offset")))
                                                    .then(argument("direction", StringArgumentType.string())
                                                            .executes((ctx) -> movePrisonBoundaries(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"), StringArgumentType.getString(ctx, "direction"), IntegerArgumentType.getInteger(ctx,"offset"))))))
                                    .then(literal("cell")
                                            .then(argument("cellName", StringArgumentType.string()).suggests(CELL_SUGGESTION_PROVIDER)
                                                    .then(argument("offset", IntegerArgumentType.integer())
                                                            .executes((ctx) -> moveCellBoundaries(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"), StringArgumentType.getString(ctx, "cellName"), null, IntegerArgumentType.getInteger(ctx,"offset")))
                                                            .then(argument("direction", StringArgumentType.string())
                                                                    .executes((ctx) -> moveCellBoundaries(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"), StringArgumentType.getString(ctx, "cellName"), StringArgumentType.getString(ctx, "direction"), IntegerArgumentType.getInteger(ctx,"offset"))))))))
                            .then(literal("expand")
                                    .then(literal("prison")
                                    .then(argument("offset", IntegerArgumentType.integer())
                                            .executes((ctx) -> changePrisonBoundaries(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"), null, IntegerArgumentType.getInteger(ctx,"offset"), true))
                                            .then(argument("direction", StringArgumentType.string())
                                                    .executes((ctx) -> changePrisonBoundaries(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"), StringArgumentType.getString(ctx, "direction"), IntegerArgumentType.getInteger(ctx,"offset"), true)))))
                                    .then(literal("cell")
                                            .then(argument("cellName", StringArgumentType.string()).suggests(CELL_SUGGESTION_PROVIDER)
                                                    .then(argument("offset", IntegerArgumentType.integer())
                                                            .executes((ctx) -> changeCellBoundaries(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"), StringArgumentType.getString(ctx, "cellName"), null, IntegerArgumentType.getInteger(ctx,"offset"), true))
                                                            .then(argument("direction", StringArgumentType.string())
                                                                    .executes((ctx) -> changeCellBoundaries(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"), StringArgumentType.getString(ctx, "cellName"), StringArgumentType.getString(ctx, "direction"), IntegerArgumentType.getInteger(ctx,"offset"), true)))))))
                            .then(literal("shrink")
                                    .then(literal("prison")
                                    .then(argument("offset", IntegerArgumentType.integer())
                                            .executes((ctx) -> changePrisonBoundaries(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"), null, IntegerArgumentType.getInteger(ctx,"offset"), false))
                                            .then(argument("direction", StringArgumentType.string())
                                                    .executes((ctx) -> changePrisonBoundaries(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"), StringArgumentType.getString(ctx, "direction"), IntegerArgumentType.getInteger(ctx,"offset"), false)))))
                                    .then(literal("cell")
                                            .then(argument("cellName", StringArgumentType.string()).suggests(CELL_SUGGESTION_PROVIDER)
                                                    .then(argument("offset", IntegerArgumentType.integer())
                                                            .executes((ctx) -> changeCellBoundaries(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"), StringArgumentType.getString(ctx, "cellName"), null, IntegerArgumentType.getInteger(ctx,"offset"), false))
                                                            .then(argument("direction", StringArgumentType.string())
                                                                    .executes((ctx) -> changeCellBoundaries(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"), StringArgumentType.getString(ctx, "cellName"), StringArgumentType.getString(ctx, "direction"), IntegerArgumentType.getInteger(ctx,"offset"), false)))))))
                            .then(literal("create")
                                    .then(argument("cellName", StringArgumentType.string())
                                            .then(argument("startPoint", BlockPosArgumentType.blockPos())
                                                    .then(argument("endPoint", BlockPosArgumentType.blockPos())
                                                            .then(argument("maxPlayersInCell", IntegerArgumentType.integer())
                                                                    .executes((ctx) -> newCell(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), StringArgumentType.getString(ctx, "prisonName"), StringArgumentType.getString(ctx, "cellName"), IntegerArgumentType.getInteger(ctx, "maxPlayersInCell"), BlockBox.create(BlockPosArgumentType.getLoadedBlockPos(ctx, "startPoint"), BlockPosArgumentType.getLoadedBlockPos(ctx, "endPoint")))))))))))));

        });
    }

    private static int takeoffHandcuffs(ServerCommandSource source, Entity argumentEntity) throws CommandSyntaxException {
        if(argumentEntity != null && !(argumentEntity instanceof LivingEntity)) throw ENTITY_CANT_BE_LEASHED.create();

        LivingEntity entity = argumentEntity == null ? source.getPlayer() : (LivingEntity) argumentEntity;
        if(entity == null) throw INVALID_ENTITY.create();

        ILivingEntityAccessor accessor = (ILivingEntityAccessor) entity;
        if(!accessor.prisonplus$isLeashed()) throw ENTITY_IS_FREE.create();
        entity.getWorld().playSound(null, entity.getBlockPos(), SoundEvents.ENTITY_LEASH_KNOT_BREAK, SoundCategory.PLAYERS, 50, 0.5f);
        accessor.prisonplus$detach(true);

        source.sendFeedback(() -> Text.literal(entity.isPlayer() ? "Игрок " : "Сущность ").formatted(Formatting.YELLOW)
                .append(Text.literal(entity.getDisplayName().getString()).formatted(Formatting.GREEN))
                .append(Text.literal(" был" +(entity.isPlayer() ? "" : "а")+ " освобожден" + (entity.isPlayer() ? "" : "а")).formatted(Formatting.YELLOW)), false);
        return 1;
    }

    private static int reloadConfig(ServerCommandSource source) {
        ConfigurationManager.onInit();
        PrisonItems.recompileStacks();

        source.sendFeedback(() -> Text.literal("Конфиг ").formatted(Formatting.GREEN)
                .append(Text.literal("был перезагружен").formatted(Formatting.YELLOW)), false);

        return 1;
    }

    private static int getItem(ServerCommandSource source, String name, ServerPlayerEntity argumentPlayer) throws CommandSyntaxException {
        ServerPlayerEntity player = argumentPlayer == null ? source.getPlayer() : argumentPlayer;
        if(player == null) throw PLAYER_REQUIRED.create();

        switch (name){
            case "handcuffs" -> player.giveItemStack(PrisonItems.HANDCUFFS.copy());
            case "baton" -> player.giveItemStack(PrisonItems.BATON.copy());
            case "scissors" -> player.giveItemStack(PrisonItems.SCISSORS.copy());
        }
        return 1;
    }

    private static int jailPlayer(ServerCommandSource source, ServerPlayerEntity player, String timeArgument, String type, String prisonName, ServerWorld argumentWorld, String cellName, String reason) throws CommandSyntaxException {
        if(timeArgument == null || timeArgument.isEmpty()) throw INVALID_TIME.create();
        int time;
        try{
            int modifier = PrisonUtils.TIME_UNITS.getOrDefault(timeArgument.substring(timeArgument.length() - 1), 1);
            time = Integer.parseInt(timeArgument.replaceAll("[\\D]", ""))*modifier;
        }catch (Exception e) {
            throw INVALID_TIME.create();
        }

        IPlayerAccessor accessor = (IPlayerAccessor) player;
        ServerWorld world = argumentWorld == null ? source.getWorld() : argumentWorld;
        if(!accessor.isInJail()) {
            Prison prison = PrisonUtils.getPrisonOrThrow(world, prisonName);
            if(!type.equals("irl") && !type.equals("igt")) throw WRONG_TYPE.create();
            PrisonCell cell = cellName == null ? prison.getFreeCellOrThrow() : prison.getCell(cellName);
            if(cell == null) throw CELL_NOT_FOUND_ERROR.create();

            if(!source.hasPermissionLevel(2)){
                if(!PrisonPlus.config.prison.policeJailBypassHandcuffs && !((ILivingEntityAccessor)player).prisonplus$isLeashed()) throw HANDCUFFS_REQUIRED.create();
                if(player.getWorld() != world) throw PRISON_IN_ANOTHER_DIMENSION.create();
                if(PrisonPlus.config.prison.policeJailRange != -1 && !prison.getPrisonCenter().isWithinDistance(player.getPos(), PrisonPlus.config.prison.policeJailRange)) throw IN_RANGE_REQUIRED.create();
            }
            ((ILivingEntityAccessor)accessor).prisonplus$detach(!PrisonPlus.config.prison.keepPlayersInHandcuffsWhenPutInPrison);
            accessor.jail(time, type.equals("irl"), prison, cell, reason);


            source.sendFeedback(() -> Text.literal("Игрок ").formatted(Formatting.YELLOW)
                    .append(Text.literal(player.getName().getString()).formatted(Formatting.GREEN))
                    .append(Text.literal(" был посажен в тюрьму ").formatted(Formatting.YELLOW))
                    .append(Text.literal(accessor.getPrison().getName()).formatted(Formatting.GREEN))
                    .append(Text.literal(" на ").formatted(Formatting.YELLOW))
                    .append(Text.literal(timeArgument).formatted(Formatting.GREEN))
                    .append(Text.literal(" по причине ").formatted(Formatting.YELLOW))
                    .append(Text.literal(reason).formatted(Formatting.RED)), true);
        }else{
            throw PLAYER_IS_IN_PRISON.create();
        }
        return 1;
    }

    private static int releasePlayer(ServerCommandSource source, ServerPlayerEntity player) throws CommandSyntaxException {
        IPlayerAccessor accessor = (IPlayerAccessor) player;
        if(accessor.isInJail()) {
            source.sendFeedback(() -> Text.literal("Игрок ").formatted(Formatting.YELLOW)
                    .append(Text.literal(player.getName().getString()).formatted(Formatting.GREEN))
                    .append(Text.literal(" был выпущен из ").formatted(Formatting.YELLOW))
                    .append(Text.literal(accessor.getPrison().getName()).formatted(Formatting.GREEN)), true);
            accessor.releaseFromJail();
        }else{
            throw PLAYER_ISNT_IN_PRISON.create();
        }
        return 1;
    }

    private static int subscribe(ServerCommandSource source, String prisonName, @Nullable ServerWorld argumentWorld, @Nullable ServerPlayerEntity argumentPlayer) throws CommandSyntaxException {
        ServerPlayerEntity player = argumentPlayer == null ? source.getPlayer() : argumentPlayer;
        if(player == null) throw PLAYER_REQUIRED.create();
        ServerWorld world = argumentWorld == null ? source.getWorld() : argumentWorld;

        if(!PrisonNetworking.subscribedTo(prisonName, player)) {

            Prison prison =  ((IWorldPrisonAccessor) world).getPrisonOrThrow(prisonName);
            PrisonNetworking.subscribeToPrison(prison, world, player);

            if (player == source.getPlayer()) {
                source.sendFeedback(() -> Text.literal("Вы были подписаны на ").formatted(Formatting.YELLOW)
                        .append(Text.literal(prisonName).formatted(Formatting.GREEN)), false);
            }else{
                source.sendFeedback(() -> Text.literal("Игрок ").formatted(Formatting.YELLOW)
                        .append(Text.literal(player.getName().getString()).formatted(Formatting.RED))
                        .append(Text.literal(" был подписан на").formatted(Formatting.YELLOW))
                        .append(Text.literal(prisonName).formatted(Formatting.GREEN)), false);
            }
        }else{
            PrisonNetworking.unsubscribeFromPrison(prisonName, player, true);
            if(player == source.getPlayer()) {
                source.sendFeedback(() -> Text.literal("Вы были отписаны от ").formatted(Formatting.YELLOW)
                        .append(Text.literal(prisonName).formatted(Formatting.GREEN)), false);
            }else{
                source.sendFeedback(() -> Text.literal("Игрок ").formatted(Formatting.YELLOW)
                        .append(Text.literal(player.getName().getString()).formatted(Formatting.RED))
                        .append(Text.literal(" был отписан от").formatted(Formatting.YELLOW))
                        .append(Text.literal(prisonName).formatted(Formatting.GREEN)), false);
            }
        }
        return 1;
    }


    private static int newPrison(ServerCommandSource source, BlockBox box, String prisonName, ServerWorld argumentWorld) throws CommandSyntaxException {
        ServerWorld world = argumentWorld == null ? source.getWorld() : argumentWorld;
        IWorldPrisonAccessor accessor = (IWorldPrisonAccessor) world;

        if(accessor.hasIntersections(box)) {
            if(!PrisonPlus.config.prison.allowPrisonIntersection) throw PRISON_INTERSECTION_ERROR.create();
            if(PrisonPlus.config.prison.warnPrisonIntersection) source.sendFeedback(() -> Text.literal("Тюрьма ").formatted(Formatting.YELLOW).append(Text.literal(prisonName).formatted(Formatting.GREEN)).append(Text.literal(" пересекается с другой.").formatted(Formatting.YELLOW)), false);
        }
        if(accessor.hasPrison(prisonName)) throw DUPLICATE_ERROR.create();
        Prison prison = new Prison(prisonName, box, world.getRegistryKey(), false);
        accessor.newPrison(prison);

        if(PrisonPlus.config.prison.autoSubscribeToPrison && source.isExecutedByPlayer()) PrisonNetworking.subscribeToPrison(prison, world, source.getPlayer());

        source.sendFeedback(() -> Text.literal("Тюрьма ").formatted(Formatting.YELLOW)
                .append(Text.literal(prisonName).formatted(Formatting.GREEN))
                .append(Text.literal(" была создана в ").formatted(Formatting.YELLOW))
                .append(Text.literal(world.getDimensionKey().getValue().toString()).formatted(Formatting.GREEN)), true);
        return 1;
    }

    private static int newCell(ServerCommandSource source, ServerWorld argumentWorld, String name, String cellName, int maxPlayersInCell, BlockBox boundary) throws CommandSyntaxException {
        ServerWorld world = argumentWorld == null ? source.getWorld() : argumentWorld;

        Prison prison = PrisonUtils.getPrisonOrThrow(world, name);
        if(prison.hasCell(cellName)) throw DUPLICATE_ERROR.create();
        PrisonCell cell = prison.createCell(cellName, boundary, maxPlayersInCell);

        if(PrisonUtils.intersects(cellName, cell.getBounds(), prison.getCells().values())) {
            if(!PrisonPlus.config.prison.allowCellIntersection) throw CELL_INTERSECTION_ERROR.create();
            if(PrisonPlus.config.prison.warnCellIntersection) source.sendFeedback(() -> Text.literal("Клетка ").formatted(Formatting.YELLOW).append(Text.literal(cellName).formatted(Formatting.GREEN)).append(Text.literal(" будет пересекаться с другой.").formatted(Formatting.YELLOW)), false);
        }
        if(PrisonUtils.outsideOf(cell.getBounds(), prison.getBounds())) {
            if(!PrisonPlus.config.prison.allowCellOutsideOfPrison) throw CELL_OUTSIDE_ERROR.create();
            if(PrisonPlus.config.prison.warnCellOutsideOfPrison) source.sendFeedback(() -> Text.literal("Клетка ").formatted(Formatting.YELLOW).append(Text.literal(cellName).formatted(Formatting.GREEN)).append(Text.literal(" будет находиться вне тюрьмы.").formatted(Formatting.YELLOW)), false);
        }

        prison.addCell(cell);
        source.sendFeedback(() -> Text.literal("Клетка ").formatted(Formatting.YELLOW).append(Text.literal(cellName).formatted(Formatting.GREEN)).append(Text.literal(" была создана в ").formatted(Formatting.YELLOW)).append(Text.literal(name).formatted(Formatting.GREEN)), true);

        return 1;
    }

    private static int removePrison(ServerCommandSource source, String prisonName, ServerWorld argumentWorld) throws CommandSyntaxException {
        ServerWorld world = argumentWorld == null ? source.getWorld() : argumentWorld;
        IWorldPrisonAccessor accessor = (IWorldPrisonAccessor) world;
        if(!accessor.hasPrison(prisonName)) throw PRISON_NOT_FOUND_ERROR.create();
        accessor.removePrison(prisonName);
        source.sendFeedback(() -> Text.literal("Тюрьма ").formatted(Formatting.YELLOW)
                .append(Text.literal(prisonName).formatted(Formatting.GREEN))
                .append(Text.literal(" была удалена в ").formatted(Formatting.YELLOW))
                .append(Text.literal(world.getDimensionKey().getValue().toString()).formatted(Formatting.GREEN)), true);

        return 1;
    }

    private static int removeCell(ServerCommandSource source, ServerWorld argumentWorld, String prisonName, String cellName) throws CommandSyntaxException {
        ServerWorld world = argumentWorld == null ? source.getWorld() : argumentWorld;
        Prison prison = PrisonUtils.getPrisonOrThrow(world, cellName);
        if(!prison.hasCell(cellName)) throw CELL_NOT_FOUND_ERROR.create();
        prison.removeCell(cellName);

        source.sendFeedback(() -> Text.literal("Клетка ").formatted(Formatting.YELLOW).append(Text.literal(cellName).formatted(Formatting.GREEN)).append(Text.literal(" была удалена в ").formatted(Formatting.YELLOW)).append(Text.literal(prisonName).formatted(Formatting.GREEN)), true);
        return 1;
    }

    private static int setPolicy(ServerCommandSource source, ServerWorld argumentWorld, String prisonName, String playerGroup, String allowedInteraction, boolean allow) throws CommandSyntaxException {
        ServerWorld world = argumentWorld == null ? source.getWorld() : argumentWorld;
        Prison prison = PrisonUtils.getPrisonOrThrow(world, prisonName);

        try{
            prison.putInteractionPolicy(PlayerGroup.valueOf(playerGroup.toUpperCase()), AllowedInteraction.valueOf(allowedInteraction.toUpperCase()), allow);

            source.sendFeedback(() -> Text.literal("Для группы ")
                    .formatted(Formatting.YELLOW).append(Text.literal(playerGroup)
                            .formatted(Formatting.GREEN)).append(Text.literal(" была "+(allow ? "установлена" : "отозвана")+" возможность взаимодействия ")
                            .formatted(Formatting.YELLOW)).append(Text.literal(allowedInteraction).formatted(Formatting.GREEN)), true);
        }catch (IllegalArgumentException e){
            throw WRONG_POLICY.create();
        }

        return 1;
    }

    private static int setCanExitCells(ServerCommandSource source, ServerWorld argumentWorld, String prisonName, boolean can) throws CommandSyntaxException {
        ServerWorld world = argumentWorld == null ? source.getWorld() : argumentWorld;
        Prison prison = PrisonUtils.getPrisonOrThrow(world, prisonName);
        prison.setCanExitCells(can);

        source.sendFeedback(() -> Text.literal("Установлено правило выхода заключенных из клеток на ")
                .formatted(Formatting.YELLOW).append(Text.literal(String.valueOf(can))
                        .formatted(Formatting.GREEN)), true);


        return 1;
    }

    private static int setMaxPlayerCountInCell(ServerCommandSource source, ServerWorld argumentWorld, String prisonName, String cellName, int amount) throws CommandSyntaxException {
        ServerWorld world = argumentWorld == null ? source.getWorld() : argumentWorld;
        Prison prison = PrisonUtils.getPrisonOrThrow(world, prisonName);
        PrisonUtils.getCellOrThrow(world, prison, cellName).setPlayerCap(amount);

        source.sendFeedback(() -> Text.literal("Установлен лимит игроков в клетке ")
                .formatted(Formatting.YELLOW).append(Text.literal(String.valueOf(cellName))
                        .formatted(Formatting.GREEN).append(Text.literal(" на ").formatted(Formatting.YELLOW)).append(Text.literal(String.valueOf(amount)).formatted(Formatting.GREEN))), true);


        return 1;
    }

    private static int moveCellBoundaries(ServerCommandSource source, ServerWorld argumentWorld, String prisonName, String cellName, String directionArgument, int amount) throws CommandSyntaxException {
        ServerWorld world = argumentWorld == null ? source.getWorld() : argumentWorld;
        Direction direction = directionArgument == null ? (source.getPlayerOrThrow().getPitch() > 60 ? Direction.DOWN : (source.getPlayerOrThrow().getPitch() < -60 ? Direction.UP : source.getPlayerOrThrow().getHorizontalFacing())) : Direction.byName(directionArgument);
        if(direction == null) throw WRONG_DIRECTION_ERROR.create();

        Prison prison = PrisonUtils.getPrisonOrThrow(world, prisonName);
        if(!prison.hasCell(cellName)) throw CELL_NOT_FOUND_ERROR.create();
        BlockBox moved = prison.moveCell(cellName, direction, amount);

        if(PrisonUtils.intersects(cellName, moved, prison.getCells().values())) {
            if(!PrisonPlus.config.prison.allowCellIntersection) throw CELL_INTERSECTION_ERROR.create();
            if(PrisonPlus.config.prison.warnCellIntersection) source.sendFeedback(() -> Text.literal("Клетка ").formatted(Formatting.YELLOW).append(Text.literal(cellName).formatted(Formatting.GREEN)).append(Text.literal(" будет пересекаться с другой.").formatted(Formatting.YELLOW)), false);
        }
        if(PrisonUtils.outsideOf(moved, prison.getBounds())) {
            if(!PrisonPlus.config.prison.allowCellOutsideOfPrison) throw CELL_OUTSIDE_ERROR.create();
            if(PrisonPlus.config.prison.warnCellOutsideOfPrison) if(PrisonPlus.config.prison.warnCellIntersection) source.sendFeedback(() -> Text.literal("Клетка ").formatted(Formatting.YELLOW).append(Text.literal(cellName).formatted(Formatting.GREEN)).append(Text.literal(" будет находиться вне тюрьмы.").formatted(Formatting.YELLOW)), false);
        }

        prison.setCellBoundaries(cellName, moved);

        source.sendFeedback(() -> Text.literal("Границы клетки ")
                .formatted(Formatting.YELLOW).append(Text.literal(cellName)
                        .formatted(Formatting.GREEN)).append(Text.literal(" были сдвинуты в направлении ")
                        .formatted(Formatting.YELLOW)).append(Text.literal(direction.asString()).formatted(Formatting.GREEN)), true);

        return 1;
    }

    private static int movePrisonBoundaries(ServerCommandSource source, ServerWorld argumentWorld, String prisonName, String directionArgument, int amount) throws CommandSyntaxException {
        ServerWorld world = argumentWorld == null ? source.getWorld() : argumentWorld;
        Direction direction = directionArgument == null ? (source.getPlayerOrThrow().getPitch() > 60 ? Direction.DOWN : (source.getPlayerOrThrow().getPitch() < -60 ? Direction.UP : source.getPlayerOrThrow().getHorizontalFacing())) : Direction.byName(directionArgument);
        if(direction == null) throw WRONG_DIRECTION_ERROR.create();

        Prison prison = PrisonUtils.getPrisonOrThrow(world, prisonName);
        BlockBox moved = prison.move(direction, amount);
        if(PrisonUtils.hasCellsOutside(moved, prison.getCells().values())) {
            if(!PrisonPlus.config.prison.allowCellOutsideOfPrison) throw CELL_OUTSIDE_ERROR.create();
            if(PrisonPlus.config.prison.warnCellOutsideOfPrison) source.sendFeedback(() -> Text.literal("Некоторые клетки ").formatted(Formatting.YELLOW).append(Text.literal(" будут находиться вне тюрьмы.").formatted(Formatting.YELLOW)), false);
        }
        prison.setPrisonBoundaries(moved);

        source.sendFeedback(() -> Text.literal("Границы тюрьмы ")
                .formatted(Formatting.YELLOW).append(Text.literal(prisonName)
                        .formatted(Formatting.GREEN)).append(Text.literal(" были сдвинуты в направлении ")
                        .formatted(Formatting.YELLOW)).append(Text.literal(direction.asString()).formatted(Formatting.GREEN)), true);
        return 1;
    }


    private static int changePrisonBoundaries(ServerCommandSource source, ServerWorld argumentWorld, String prisonName, String directionArgument, int amount, boolean expand) throws CommandSyntaxException {
        ServerWorld world = argumentWorld == null ? source.getWorld() : argumentWorld;
        Direction direction = directionArgument == null ? (source.getPlayerOrThrow().getPitch() > 60 ? Direction.DOWN : (source.getPlayerOrThrow().getPitch() < -60 ? Direction.UP : source.getPlayerOrThrow().getHorizontalFacing())) : Direction.byName(directionArgument);
        if(direction == null) throw WRONG_DIRECTION_ERROR.create();

        Prison prison = PrisonUtils.getPrisonOrThrow(world, prisonName);
        BlockBox expanded = expand ? prison.expand(direction, amount) : prison.shrink(direction, amount);
        if(PrisonUtils.hasCellsOutside(expanded, prison.getCells().values())) {
            if(!PrisonPlus.config.prison.allowCellOutsideOfPrison) throw CELL_OUTSIDE_ERROR.create();
            if(PrisonPlus.config.prison.warnCellOutsideOfPrison) source.sendFeedback(() -> Text.literal("Некоторые клетки ").formatted(Formatting.YELLOW).append(Text.literal(" будут находиться вне тюрьмы.").formatted(Formatting.YELLOW)), false);
        }
        prison.setPrisonBoundaries(expanded);

        source.sendFeedback(() -> Text.literal("Границы тюрьмы ")
                .formatted(Formatting.YELLOW).append(Text.literal(prisonName)
                        .formatted(Formatting.GREEN)).append(Text.literal(" были "+(expand ? "расширены" : "уменьшены") +" в направлении ")
                        .formatted(Formatting.YELLOW)).append(Text.literal(direction.asString()).formatted(Formatting.GREEN)), true);
        return 1;
    }

    private static int changeCellBoundaries(ServerCommandSource source, ServerWorld argumentWorld, String prisonName, String cellName, String directionArgument, int amount, boolean expand) throws CommandSyntaxException {
        ServerWorld world = argumentWorld == null ? source.getWorld() : argumentWorld;
        Direction direction = directionArgument == null ? (source.getPlayerOrThrow().getPitch() > 60 ? Direction.DOWN : (source.getPlayerOrThrow().getPitch() < -60 ? Direction.UP : source.getPlayerOrThrow().getHorizontalFacing())) : Direction.byName(directionArgument);
        if(direction == null) throw WRONG_DIRECTION_ERROR.create();

        Prison prison = PrisonUtils.getPrisonOrThrow(world, prisonName);
        if(!prison.hasCell(cellName)) throw CELL_NOT_FOUND_ERROR.create();
        BlockBox expanded = expand ? prison.expandCell(cellName, direction, amount) : prison.shrinkCell(cellName, direction, amount);

        if(PrisonUtils.intersects(cellName, expanded, prison.getCells().values())) {
            if(!PrisonPlus.config.prison.allowCellIntersection) throw CELL_INTERSECTION_ERROR.create();
            if(PrisonPlus.config.prison.warnCellIntersection) source.sendFeedback(() -> Text.literal("Клетка ").formatted(Formatting.YELLOW).append(Text.literal(cellName).formatted(Formatting.GREEN)).append(Text.literal(" будет пересекаться с другой.").formatted(Formatting.YELLOW)), false);
        }
        if(PrisonUtils.outsideOf(expanded, prison.getBounds())) {
            if(!PrisonPlus.config.prison.allowCellOutsideOfPrison) throw CELL_OUTSIDE_ERROR.create();
            if(PrisonPlus.config.prison.warnCellOutsideOfPrison) if(PrisonPlus.config.prison.warnCellIntersection) source.sendFeedback(() -> Text.literal("Клетка ").formatted(Formatting.YELLOW).append(Text.literal(cellName).formatted(Formatting.GREEN)).append(Text.literal(" будет находиться вне тюрьмы.").formatted(Formatting.YELLOW)), false);
        }

        prison.setCellBoundaries(cellName, expanded);

        source.sendFeedback(() -> Text.literal("Границы клетки ")
                .formatted(Formatting.YELLOW).append(Text.literal(cellName)
                        .formatted(Formatting.GREEN)).append(Text.literal(" были " +(expand ? "расширены" : "уменьшены")+ " в направлении ")
                        .formatted(Formatting.YELLOW)).append(Text.literal(direction.asString()).formatted(Formatting.GREEN)), true);

        return 1;
    }


    private static int printAllPrisons(ServerCommandSource source, ServerWorld argumentWorld) {
        ServerWorld world = argumentWorld == null ? source.getWorld() : argumentWorld;

        IWorldPrisonAccessor prisonAccessor = (IWorldPrisonAccessor) world;
        MutableText text = Text.literal("Найдено ").formatted(Formatting.YELLOW).append(Text.literal(String.valueOf(prisonAccessor.getPrisonAmount())).formatted(Formatting.GREEN)).append(Text.literal(" тюрем в ").formatted(Formatting.YELLOW)).append(Text.literal(world.getDimensionKey().getValue().toString()).formatted(Formatting.GREEN));


        for (Prison prison : prisonAccessor.getPrisonsInDimension().values())
            text.append(Text.literal("\n * ").formatted(Formatting.YELLOW)).append(Text.literal(prison.getName()).formatted(Formatting.GREEN)).append(Text.literal(" на ").formatted(Formatting.YELLOW)).append(Text.literal(prison.getPrisonCenter().toShortString()).formatted(Formatting.GREEN));
        source.sendFeedback(() -> text, false);

        return 1;
    }

    private static int printAllCells(ServerCommandSource source, ServerWorld argumentWorld, String prisonName) throws CommandSyntaxException {
        ServerWorld world = argumentWorld == null ? source.getWorld() : argumentWorld;

        Prison prison = PrisonUtils.getPrisonOrThrow(world, prisonName);
        MutableText text = Text.literal("Найдено ").formatted(Formatting.YELLOW).append(Text.literal(String.valueOf(prison.getCellAmount())).formatted(Formatting.GREEN)).append(Text.literal(" клеток в тюрьме ").formatted(Formatting.YELLOW)).append(Text.literal(prisonName).formatted(Formatting.GREEN));

        for (PrisonCell cell : prison.getCells().values()) {
            text.append(Text.literal("\n * ").formatted(Formatting.YELLOW)).append(Text.literal(cell.getName()).formatted(Formatting.GREEN)).append(Text.literal(" на ").formatted(Formatting.YELLOW)).append(Text.literal(cell.getBounds().getCenter().toShortString()).formatted(Formatting.GREEN));
        }
        source.sendFeedback(() -> text, false);
        return 1;
    }

    private static int printModInfo(ServerCommandSource source) {
        source.sendFeedback(() -> PrisonPlus.STYLED_MOD_NAME, false);
        source.sendFeedback(() -> Text.literal(" §aMod Version§f: §a" +PrisonPlus.MOD_VERSION + "\n §eMade by §l§6ForwarD_NerN §efor §bLantern X"), false);

        return 1;
    }

    private static int printPrisonerInfo(ServerCommandSource source, ServerPlayerEntity player) throws CommandSyntaxException {
        IPlayerAccessor accessor = (IPlayerAccessor) player;
        if(!accessor.isInJail()) throw PLAYER_ISNT_IN_PRISON.create();
        source.sendFeedback(() -> Text.literal("Игрок ").formatted(Formatting.YELLOW)
                .append(Text.literal(player.getName().getString()).formatted(Formatting.GREEN))
                .append(Text.literal(" находится в тюрьме ").formatted(Formatting.YELLOW))
                .append(Text.literal(accessor.getPrison().getName()).formatted(Formatting.GREEN))
                .append(Text.literal(" по причине ").formatted(Formatting.YELLOW))
                .append(Text.literal(accessor.getReason()).formatted(Formatting.RED))
                .append(Text.literal(". Оставшийся срок ").formatted(Formatting.YELLOW))
                .append(Text.literal(accessor.getTimeManager().getTimeLeft() / 20 +"s").formatted(Formatting.GREEN)), true);
        return 1;
    }

    private static int teleportToPrison(ServerCommandSource source, String prisonName, ServerPlayerEntity argumentPlayer) throws CommandSyntaxException {
        ServerPlayerEntity player = argumentPlayer == null ? source.getPlayer() : argumentPlayer;
        if(player == null) throw PLAYER_REQUIRED.create();

        IWorldPrisonAccessor accessor = (IWorldPrisonAccessor) source.getWorld();
        if(!accessor.hasPrison(prisonName)) throw PRISON_NOT_FOUND_ERROR.create();

        BlockPos pos = accessor.getPrison(prisonName).getPrisonCenter();
        player.requestTeleport(pos.getX(), pos.getY(), pos.getZ());

        if(argumentPlayer == null) {
            source.sendFeedback(() -> Text.literal("Вы были телепортированы к тюрьме ").formatted(Formatting.YELLOW)
                    .append(Text.literal(prisonName).formatted(Formatting.GREEN)), false);
        }else{
            source.sendFeedback(() -> Text.literal("Игрок ").formatted(Formatting.YELLOW).append(player.getName().copy().formatted(Formatting.GREEN)).append(" был телепортирован к тюрьме ").formatted(Formatting.YELLOW)
                    .append(Text.literal(prisonName).formatted(Formatting.GREEN)), false);
        }
        return 1;
    }


}
