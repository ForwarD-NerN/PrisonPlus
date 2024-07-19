package ru.nern.prisonplus.utils;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import ru.nern.prisonplus.structure.Prison;
import ru.nern.prisonplus.structure.PrisonCell;
import ru.nern.prisonplus.structure.PrisonTimeManager;

import java.util.Set;

public interface IPlayerAccessor
{
    void subscribe(String prison);
    void unsubscribe(String prison);
    void releaseFromJail();
    void jail(int time, boolean irl, Prison prison, PrisonCell cell, String reason);
    boolean subscribedTo(String prisonName);
    boolean isInJail();
    Prison getPrison();
    PrisonCell getCell();
    String getReason();
    BlockPos getPreJailPos();
    Identifier getPreJailWorld();
    PrisonTimeManager getTimeManager();
    Set<String> getSubscribedPrisons();
}
