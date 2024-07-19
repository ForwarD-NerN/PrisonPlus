package ru.nern.prisonplus.structure;

import com.google.common.collect.Maps;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import ru.nern.prisonplus.PrisonPlus;
import ru.nern.prisonplus.commands.PrisonCommands;
import ru.nern.prisonplus.structure.enums.AllowedInteraction;
import ru.nern.prisonplus.structure.enums.PlayerGroup;
import ru.nern.prisonplus.utils.PrisonUtils;
import xyz.nucleoid.stimuli.event.EventListenerMap;
import xyz.nucleoid.stimuli.filter.EventFilter;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Set;

public class Prison
{
    private final String name;
    private BlockBox boundaryBox;
    //Нужно для рендера, проверяет находится ли игрок в том же измерении. Проверяется при создании тюрьмы на клиенте и при заходе клиента в другой мир
    @Environment(EnvType.CLIENT) private boolean inClientDimension;
    private final Identifier dimension;
    private boolean dirty;
    //Становится true если внутри тюрьмы произошла block модификация
    public boolean modified = false;
    private boolean canExitCells = false;
    private final HashMap<String, PrisonCell> cells = Maps.newHashMap();
    private final EnumMap<PlayerGroup, Set<AllowedInteraction>> policy = Maps.newEnumMap(PlayerGroup.class);
    private final EventFilter eventFilter;
    private final EventListenerMap eventListeners;

    public Prison(String name, BlockBox boundary, RegistryKey<World> dimension, boolean isClient) {
        this.name = name;
        this.boundaryBox = boundary;
        this.dimension = dimension.getValue();
        this.policy.putAll(PrisonUtils.DEFAULT_POLICY);
        this.eventListeners = new EventListenerMap();
        if(!isClient) PrisonPlus.listenToEvents(this);

        this.eventFilter = EventFilter.box(dimension, new BlockPos(boundary.getMinX(), boundary.getMinY(), boundary.getMinZ()), new BlockPos(boundary.getMaxX(), boundary.getMaxY(), boundary.getMaxZ()));
    }

    public Prison(String name, BlockBox boundary, RegistryKey<World> dimension, EnumMap<PlayerGroup, Set<AllowedInteraction>> policyMap, boolean isClient) {
        this.name = name;
        this.boundaryBox = boundary;
        this.dimension = dimension.getValue();
        this.policy.putAll(policyMap);
        this.eventListeners = new EventListenerMap();
        if(!isClient) PrisonPlus.listenToEvents(this);

        this.eventFilter = EventFilter.box(dimension, new BlockPos(boundary.getMinX(), boundary.getMinY(), boundary.getMinZ()), new BlockPos(boundary.getMaxX(), boundary.getMaxY(), boundary.getMaxZ()));
    }

    public String getName() {
        return this.name;
    }
    public Identifier getDimension() {
        return dimension;
    }

    public void putInteractionPolicy(PlayerGroup group, AllowedInteraction interaction, boolean allow) {
        Set<AllowedInteraction> allowedInteractions = this.policy.get(group);
        if(allow) {
            allowedInteractions.add(interaction);
        }else{
            allowedInteractions.remove(interaction);
        }
        this.policy.put(group, allowedInteractions);
    }
    public Set<AllowedInteraction> getAllowedInteractions(PlayerGroup group) {
        return this.policy.get(group);
    }
    public BlockPos getPrisonCenter()
    {
        return this.boundaryBox.getCenter();
    }
    public void setCanExitCells(boolean canExitCells)
    {
        this.canExitCells = canExitCells;
    }
    public boolean canPrisonersExitCells() {
        return this.canExitCells;
    }
    public BlockBox move(Direction direction, int amount) {
        return this.getBounds().offset(direction.getOffsetX()*amount, direction.getOffsetY()*amount, direction.getOffsetZ()*amount);
    }
    public BlockBox expand(Direction direction, int amount) {
        int minX = boundaryBox.getMinX();
        int minY = boundaryBox.getMinY();
        int minZ = boundaryBox.getMinZ();
        int maxX = boundaryBox.getMaxX();
        int maxY = boundaryBox.getMaxY();
        int maxZ = boundaryBox.getMaxZ();

        if (direction.getOffsetX() < 0) {
            minX -= amount;
        } else if (direction.getOffsetX() > 0) {
            maxX += amount;
        }

        if (direction.getOffsetY() < 0) {
            minY -= amount;
        } else if (direction.getOffsetY() > 0) {
            maxY += amount;
        }

        if (direction.getOffsetZ() < 0) {
            minZ -= amount;
        } else if (direction.getOffsetZ() > 0) {
            maxZ += amount;
        }

        return new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
    public BlockBox shrink(Direction direction, int amount) {
        int minX = boundaryBox.getMinX();
        int minY = boundaryBox.getMinY();
        int minZ = boundaryBox.getMinZ();
        int maxX = boundaryBox.getMaxX();
        int maxY = boundaryBox.getMaxY();
        int maxZ = boundaryBox.getMaxZ();

        if (direction.getOffsetX() < 0) {
            minX += amount;
        } else if (direction.getOffsetX() > 0) {
            maxX -= amount;
        }

        if (direction.getOffsetY() < 0) {
            minY += amount;
        } else if (direction.getOffsetY() > 0) {
            maxY -= amount;
        }

        if (direction.getOffsetZ() < 0) {
            minZ += amount;
        } else if (direction.getOffsetZ() > 0) {
            maxZ -= amount;
        }

        return new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
    public void setPrisonBoundaries(BlockBox boundaryBox) {
        this.boundaryBox = boundaryBox;
        this.markDirty();
    }
    public BlockBox getBounds() {
        return this.boundaryBox;
    }
    public BlockBox moveCell(String name, Direction direction, int amount) {
        return this.getCell(name).moveBoundaries(direction, amount);
    }
    public BlockBox expandCell(String name, Direction direction, int amount) {
        return this.getCell(name).expandBoundaries(direction, amount);
    }
    public BlockBox shrinkCell(String name, Direction direction, int amount) {
        return this.getCell(name).shrinkBoundaries(direction, amount);
    }
    public boolean hasCell(String key) {
        return cells.containsKey(key);
    }
    public void setCellBoundaries(String name, BlockBox box) {
        this.getCell(name).setBoundaries(box);
        this.markDirty();
    }
    public PrisonCell createCell(String cellName, BlockBox boundary, int playerMax) {
        return new PrisonCell(cellName, boundary, playerMax);
    }
    public void addCell(PrisonCell cell) {
        cells.put(cell.getName(), cell);
        this.markDirty();
    }
    public void removeCell(String key) {
        cells.remove(key);
        this.markDirty();
    }
    public void removeAllCells() {
        cells.clear();
        this.markDirty();
    }
    public HashMap<String, PrisonCell> getCells() {
        return cells;
    }
    public PrisonCell getCell(String key) {
        return cells.get(key);
    }
    public PrisonCell getFreeCellOrThrow() throws CommandSyntaxException {
        for(PrisonCell cell : cells.values()) {
            if(PrisonPlus.config.prison.allowCellOverfill || cell.getPlayerCount() < cell.getMaxPlayerCap()) return cell;
        }
        throw PrisonCommands.PRISON_OVERFILLED.create();
    }
    public int getCellAmount() {
        return cells.size();
    }


    //Существует только на клиенте, нужно, чтобы быстро проверить следует ли рендерить границу тюрьмы.
    @Environment(EnvType.CLIENT)
    public boolean shouldRender() {
        return this.inClientDimension;
    }

    @Environment(EnvType.CLIENT)
    public void checkDimension(Identifier other) {
        this.inClientDimension = other.equals(dimension);
    }


    public void markDirty() {
        this.dirty = true;
    }

    public void unmarkDirty() {
        this.dirty = false;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public EventListenerMap getEventListeners() {
        return eventListeners;
    }

    public EventFilter getEventFilter() {
        return eventFilter;
    }
}
