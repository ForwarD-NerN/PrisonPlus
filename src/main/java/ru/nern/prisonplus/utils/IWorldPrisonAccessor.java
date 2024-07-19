package ru.nern.prisonplus.utils;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import ru.nern.prisonplus.structure.Prison;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public interface IWorldPrisonAccessor
{
    HashMap<String, Prison> getPrisonsInDimension();
    void newPrison(Prison prison);
    int getPrisonAmount();
    Iterator<Prison> getPrisonIterator();

    boolean hasIntersections(BlockBox box);
    boolean hasPrison(String key);
    void putPrisonData(Map<String, Prison> data);
    Prison getPrison(String key);
    Prison getPrisonOrThrow(String key) throws CommandSyntaxException;
    void removePrison(String key);
}
