package ru.nern.prisonplus.structure;

public class PrisonTimeManager
{

    private final boolean isIrl;
    private int time;
    private boolean shouldBeFree;
    public PrisonTimeManager(int time, boolean irl)
    {
        this.isIrl = irl;
        this.time = time;
    }

    public void tick()
    {
        time--;
        if(time < 1) this.shouldBeFree = true;
    }

    public boolean shouldBeFree() {
        return this.shouldBeFree;
    }

    public int getTimeLeft() {
        return this.time;
    }

    public boolean isIrl() {
        return this.isIrl;
    }
}
