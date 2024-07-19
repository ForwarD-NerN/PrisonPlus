package ru.nern.prisonplus.utils;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.*;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityAttachS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.commons.lang3.EnumUtils;
import ru.nern.prisonplus.PrisonPlus;
import ru.nern.prisonplus.commands.PrisonCommands;
import ru.nern.prisonplus.structure.Prison;
import ru.nern.prisonplus.structure.PrisonCell;
import ru.nern.prisonplus.structure.enums.AllowedInteraction;
import ru.nern.prisonplus.structure.enums.PlayerGroup;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Stream;

public class PrisonUtils
{
    public static final Object2IntMap<String> TIME_UNITS = new Object2IntOpenHashMap<>();
    public static final EnumMap<PlayerGroup, HashSet<AllowedInteraction>> DEFAULT_POLICY = Maps.newEnumMap(PlayerGroup.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(EnumMap.class, (InstanceCreator<EnumMap>) type -> new EnumMap((Class<?>) ((((ParameterizedType) type).getActualTypeArguments()))[0])).create();
    public static void savePrisonData(MinecraftServer server) {
        try {
            for (ServerWorld world : server.getWorlds()) {
                IWorldPrisonAccessor prisonAccessor = (IWorldPrisonAccessor) world;
                if (prisonAccessor.getPrisonAmount() > 0) {
                    save(deserializePrisonData(prisonAccessor.getPrisonsInDimension()), new File(server.getSavePath(WorldSavePath.ROOT).resolve("data").toFile(), "prisons_" + world.getDimensionKey().getValue().getPath() + ".json"));
                }
            }
        }catch (Exception e){
            PrisonPlus.LOGGER.error("Exception occured while saving prison data", e.getCause());
        }
    }

    public static void loadPrisonData(MinecraftServer server) {
        try{
            for(ServerWorld world : server.getWorlds()) {
                File DIMENSION_PRISON_DATA = new File(server.getSavePath(WorldSavePath.ROOT).resolve("data").toFile(), "prisons_" +world.getDimensionKey().getValue().getPath() + ".json");

                if(DIMENSION_PRISON_DATA.exists())
                {
                    ((IWorldPrisonAccessor)world).putPrisonData(serializePrisonData(JsonParser.parseString(load(DIMENSION_PRISON_DATA)).getAsJsonObject(), server));
                }

            }
        }catch (Exception e) {
            PrisonPlus.LOGGER.error("Exception occured while loading prison data", e);
        }

    }

    private static void save(JsonObject data, File file) {
        try {
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(gson.toJson(data));
            fileWriter.close();
        } catch (Exception e) {
            PrisonPlus.LOGGER.error("Error when saving PrisonPlus data", e);
        }
    }

    private static JsonObject deserializePrisonData(HashMap<String, Prison> prisons)
    {
        JsonObject root = new JsonObject();
        for(Prison prison : prisons.values()){
            JsonObject prisonObject = new JsonObject();
            prisonObject.add("bounds", boundsToJson(prison.getBounds()));
            prisonObject.addProperty("dimension", prison.getDimension().toString());
            prisonObject.addProperty("allowExitCells", prison.canPrisonersExitCells());

            JsonObject policyObject = new JsonObject();
            for(PlayerGroup group : PlayerGroup.values())
            {
                JsonArray groupObject = new JsonArray();
                prison.getAllowedInteractions(group).forEach(interaction -> groupObject.add(interaction.name().toLowerCase()));
                policyObject.add(group.name().toLowerCase(), groupObject);
            }

            prisonObject.add("policy", policyObject);

            JsonObject cells = new JsonObject();

            for(PrisonCell cell : prison.getCells().values()) {
                JsonObject cellOb = new JsonObject();
                cellOb.addProperty("maxPlayerCap", cell.getMaxPlayerCap());
                cellOb.add("bounds", boundsToJson(cell.getBounds()));

                cells.add(cell.getName(), cellOb);
            }

            prisonObject.add("cells", cells);

            root.add(prison.getName(), prisonObject);
        }
        return root;
    }

    private static Map<String, Prison> serializePrisonData(JsonObject root, MinecraftServer server)
    {
        Map<String, Prison> prisons = Maps.newHashMap();
        for(Map.Entry<String, JsonElement> prisonEntry : root.entrySet()) {
            JsonObject prisonObject = prisonEntry.getValue().getAsJsonObject();
            BlockBox bounds = boundsFromJson(prisonObject.getAsJsonObject("bounds"));
            RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.tryParse(prisonObject.get("dimension").getAsString()));
            boolean allowExitCells = prisonObject.get("allowExitCells").getAsBoolean();

            EnumMap<PlayerGroup, Set<AllowedInteraction>> policyMap = Maps.newEnumMap(PlayerGroup.class);
            JsonObject policyObject = prisonObject.getAsJsonObject("policy");

            for(PlayerGroup group : PlayerGroup.values()) {
                final Set<AllowedInteraction> allowedInteractions = Sets.newHashSet();
                JsonArray groupObject = policyObject.getAsJsonArray(group.name().toLowerCase());
                groupObject.forEach(jsonElement -> {
                    String value = jsonElement.getAsString().toUpperCase();
                    if(EnumUtils.isValidEnum(AllowedInteraction.class, value)) allowedInteractions.add(AllowedInteraction.valueOf(value));
                });
                policyMap.put(group, allowedInteractions);
            }

            Map<String, PrisonCell> cells = Maps.newHashMap();
            JsonObject cellsObject = prisonObject.getAsJsonObject("cells");


            for(Map.Entry<String, JsonElement> cellEntry : cellsObject.entrySet()){
                JsonObject cellObject = cellEntry.getValue().getAsJsonObject();
                int maxPlayerCap = cellObject.get("maxPlayerCap").getAsInt();
                BlockBox cellBounds = boundsFromJson(cellObject.getAsJsonObject("bounds"));
                cells.put(cellEntry.getKey(), new PrisonCell(cellEntry.getKey(), cellBounds, maxPlayerCap));
            }

            Prison prison = new Prison(prisonEntry.getKey(), bounds, dimension, policyMap, false);
            prison.setCanExitCells(allowExitCells);
            prison.getCells().putAll(cells);

            prisons.put(prisonEntry.getKey(), prison);
        }
        return prisons;
    }

    private static JsonObject boundsToJson(BlockBox box){
        JsonObject bounds = new JsonObject();
        bounds.addProperty("minX", box.getMinX());
        bounds.addProperty("minY", box.getMinY());
        bounds.addProperty("minZ", box.getMinZ());
        bounds.addProperty("maxX", box.getMaxX());
        bounds.addProperty("maxY", box.getMaxY());
        bounds.addProperty("maxZ", box.getMaxZ());
        return bounds;
    }

    private static BlockBox boundsFromJson(JsonObject json){
        return new BlockBox(json.get("minX").getAsInt(), json.get("minY").getAsInt(), json.get("minZ").getAsInt(), json.get("maxX").getAsInt(), json.get("maxY").getAsInt(), json.get("maxZ").getAsInt());
    }

    public static String load(File file) {
        try {
            if (file.exists()) {
                StringBuilder contentBuilder = new StringBuilder();
                try (Stream<String> stream = Files.lines(file.toPath(), StandardCharsets.UTF_8)) {
                    stream.forEach(s -> contentBuilder.append(s).append("\n"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return contentBuilder.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static BlockBox toBlockBox(int[] boundaries) {
        return new BlockBox(boundaries[0], boundaries[1], boundaries[2], boundaries[3], boundaries[4], boundaries[5]);
    }

    public static int[] fromBlockBox(BlockBox box) {
        return new int[]{box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ()};
    }

    public static boolean outsideOf(BlockBox target, BlockBox main) {
        return !main.contains(target.getMaxX(), target.getMaxY(), target.getMaxZ()) || !main.contains(target.getMinX(), target.getMinY(), target.getMinZ());
    }

    public static boolean outsideOf(BlockPos pos, BlockBox main) {
        return !main.contains(pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean hasCellsOutside(BlockBox box, Collection<PrisonCell> cells)
    {
        for(PrisonCell cell : cells)
        {
            if(outsideOf(cell.getBounds(), box)) return true;
        }
        return false;
    }

    public static boolean intersects(String targetName, BlockBox target, Collection<PrisonCell> cells) {
        for(PrisonCell cell : cells) {
            if(!cell.getName().equals(targetName) && cell.getBounds().intersects(target)) return true;
        }
        return false;
    }

    @Nullable
    public static Prison getPrisonFromWorld(ServerWorld world, String prisonName) {
        return ((IWorldPrisonAccessor)world).getPrison(prisonName);
    }

    public static Prison getPrisonOrThrow(ServerWorld world, String name) throws CommandSyntaxException {
        return ((IWorldPrisonAccessor)world).getPrisonOrThrow(name);
    }

    public static PrisonCell getCellOrThrow(ServerWorld world, Prison prison, String name) throws CommandSyntaxException {
        PrisonCell cell = prison.getCell(name);
        if(cell == null) throw PrisonCommands.CELL_NOT_FOUND_ERROR.create();
        return cell;
    }

    public static long getUnixTimestamp() {
        return System.currentTimeMillis() / 1000L;
    }


    public static ServerWorld getWorld(Identifier identifier, MinecraftServer server)
    {
        return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, identifier));
    }

    public static void attachLeash(LivingEntity toAttach, Entity player, ServerWorld world) {
        world.getChunkManager().sendToOtherNearbyPlayers(player, new EntityAttachS2CPacket(toAttach, player));
    }

    static {
        TIME_UNITS.put("d", 1728000);
        TIME_UNITS.put("h", 72000);
        TIME_UNITS.put("m", 1200);
        TIME_UNITS.put("s", 20);
        TIME_UNITS.put("t", 1);
        TIME_UNITS.put("", 1);

        HashSet<AllowedInteraction> PRISONERS = Sets.newHashSet();
        PRISONERS.add(AllowedInteraction.USE_ITEMS);
        PRISONERS.add(AllowedInteraction.USE_BLOCKS);
        DEFAULT_POLICY.put(PlayerGroup.PRISONERS, PRISONERS);

        HashSet<AllowedInteraction> DEFAULT = Sets.newHashSet();
        DEFAULT.add(AllowedInteraction.USE_ITEMS);
        DEFAULT.add(AllowedInteraction.USE_BLOCKS);
        DEFAULT.add(AllowedInteraction.PLACE_BLOCKS);
        DEFAULT.add(AllowedInteraction.BREAK_BLOCK);
        DEFAULT.add(AllowedInteraction.HIT_ENTITIES);
        DEFAULT.add(AllowedInteraction.INTERACT_ENTITIES);
        DEFAULT.add(AllowedInteraction.PLACE_FLUIDS);
        DEFAULT.add(AllowedInteraction.PICKUP_ITEMS);
        DEFAULT.add(AllowedInteraction.THROW_ITEMS);
        DEFAULT.add(AllowedInteraction.CRAFT_ITEMS);
        DEFAULT_POLICY.put(PlayerGroup.EVERYONE, DEFAULT);
    }
}
