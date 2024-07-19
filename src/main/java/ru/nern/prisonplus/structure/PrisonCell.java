package ru.nern.prisonplus.structure;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.Direction;
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.UUID;

public class PrisonCell
{
    private final String name;
    private BlockBox boundaryBox;
    private int playerCap;
    private List<UUID> jailedPlayers = Lists.newArrayList();
    public PrisonCell(String name, BlockBox boundaryBox, int playerCap) {
        this.name = name;
        this.boundaryBox = boundaryBox;
        this.playerCap = playerCap;
    }

    public String getName() {
        return name;
    }

    public BlockBox getBounds() {
        return boundaryBox;
    }


    public BlockBox moveBoundaries(Direction direction, int amount) {
        return this.getBounds().offset(direction.getOffsetX()*amount, direction.getOffsetY()*amount, direction.getOffsetZ()*amount);
    }

    public BlockBox expandBoundaries(Direction direction, int amount) {
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

    public BlockBox shrinkBoundaries(Direction direction, int amount) {
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

    public void setBoundaries(BlockBox boundaryBox) {
        this.boundaryBox = boundaryBox;
    }

    public void addPlayer(UUID uuid) {
        jailedPlayers.add(uuid);
    }

    public void removePlayer(UUID uuid) {
        jailedPlayers.remove(uuid);
    }

    public int getPlayerCount() {
        return jailedPlayers.size();
    }

    public int getMaxPlayerCap() {
        return playerCap;
    }

    public void setPlayerCap(int playerCap) {
        this.playerCap = playerCap;
    }
}
