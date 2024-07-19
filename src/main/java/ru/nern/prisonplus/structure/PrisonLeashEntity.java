package ru.nern.prisonplus.structure;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.TurtleEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.nern.prisonplus.PrisonPlus;
import ru.nern.prisonplus.utils.ILivingEntityAccessor;

public class PrisonLeashEntity extends TurtleEntity {
    @Nullable
    private Entity leashedEntity;
    public static final String TEAM_NAME = "prisonplus_leash";
    public PrisonLeashEntity(World world, LivingEntity target) {
        super(EntityType.TURTLE, world);
        setInvisible(true);

        MinecraftServer server = getServer();
        if (server != null) {
            ServerScoreboard scoreboard = server.getScoreboard();

            Team team = scoreboard.getTeam(TEAM_NAME);
            if (team == null) {
                team = scoreboard.addTeam(TEAM_NAME);
            }
            if (team.getCollisionRule() != AbstractTeam.CollisionRule.NEVER) team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
            if(team.shouldShowFriendlyInvisibles()) team.setShowFriendlyInvisibles(false);

            scoreboard.addPlayerToTeam(getEntityName(), team);
            scoreboard.addPlayerToTeam(target.getEntityName(), team);
        }
    }

    @Override
    public float getHealth() {
        return 1.0F;
    }

    @Override
    protected void initGoals() {}

    @Override protected void jump() {}
    @Override
    public void pushAwayFrom(Entity entity) {}
    @Override
    protected void pushAway(Entity entity) {
    }
    @Override
    public boolean collidesWith(Entity other) {
        return false;
    }

    @Override
    public boolean canBeLeashedBy(PlayerEntity player) {
        return false;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound compound = super.writeNbt(nbt);
        compound.putFloat("Health", 0);
        return compound;
    }

    @Override
    public boolean saveNbt(NbtCompound nbt) {
        return false;
    }


    @Override
    public void tick() {
        super.tick();
        if(leashedEntity != null && leashedEntity.isAlive()){
            this.setPosition(leashedEntity.getPos().add(0, PrisonPlus.config.items.leashHeightOffset, 0));
            /*
            float distance = leashedEntity.distanceTo(this);
            if(distance > 0.5) {
                double d = (this.getX() - leashedEntity.getX()) / (double)distance;
                double e = (this.getY() - leashedEntity.getY()) / (double)distance;
                double g = (this.getZ() - leashedEntity.getZ()) / (double)distance;
                leashedEntity.addVelocity(Math.copySign(d * d * 0.4, d), Math.copySign(e * e * 0.4, e), Math.copySign(g * g * 0.4, g));

                Entity holdingEntity = getHoldingEntity();
                if(leashedEntity.distanceTo(getHoldingEntity()) > PrisonPlus.config.items.leashTeleportDistance) {
                    leashedEntity.requestTeleport(holdingEntity.getX(), holdingEntity.getY(), holdingEntity.getZ());
                    this.requestTeleport(holdingEntity.getX(), holdingEntity.getY(), holdingEntity.getZ());
                }

                if (leashedEntity.isPlayer()) {
                    ((ServerPlayerEntity) leashedEntity).networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(leashedEntity));
                    leashedEntity.velocityDirty = false;
                }
            }

             */
        }else{
            setRemoved(RemovalReason.DISCARDED);
        }
    }

    @Override
    protected void updateLeash() {
        if (this.getHoldingEntity() == null || this.leashedEntity == null) return;

        if (!this.isAlive() || !this.getHoldingEntity().isAlive()) {
            this.detachLeash(true, true);
            return;
        }

        Entity entity = this.getHoldingEntity();
        if (entity != null && entity.getWorld() == this.getWorld()) {
            this.setPositionTarget(entity.getBlockPos(), 5);
            float distance = this.distanceTo(entity);
            this.updateForLeashLength(distance);

            if (PrisonPlus.config.items.leashDetachDistance > 0 && distance > PrisonPlus.config.items.leashDetachDistance) {
                this.detachLeash(true, true);
                this.goalSelector.disableControl(Goal.Control.MOVE);
            } else if (distance > 6.0f) {
                double velX = (entity.getX() - this.getX()) / (double) distance;
                double velY = (entity.getY() - this.getY()) / (double) distance;
                double velZ = (entity.getZ() - this.getZ()) / (double) distance;
                leashedEntity.setVelocity(leashedEntity.getVelocity().add(Math.copySign(velX * velX * 0.4, velX), Math.copySign(velY * velY * 0.4, velY), Math.copySign(velZ * velZ * 0.4, velZ)));
                if (leashedEntity.isPlayer()) {
                    ((ServerPlayerEntity) leashedEntity).networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(leashedEntity));
                    leashedEntity.velocityDirty = false;
                }

                if(distance > PrisonPlus.config.items.leashTeleportDistance)
                    leashedEntity.requestTeleport(entity.getX(), entity.getY(), entity.getZ());

                leashedEntity.limitFallDistance();
            }
        }
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public void detachLeash(boolean sendPacket, boolean dropItem) {
        //if(leashedEntity != null) ((ILivingEntityAccessor)this.leashedEntity).prisonplus$detach(false);
        this.leashedEntity = null;
        super.detachLeash(sendPacket, dropItem);
    }

    @Nullable
    @Override
    public ItemEntity dropItem(ItemConvertible item) {
        if(PrisonPlus.config.items.dropHandcuffs && item.asItem() == Items.LEAD) return this.dropStack(PrisonItems.HANDCUFFS.copy());
        return null;
    }

    public void setLeashedEntity(@Nullable Entity entity) {
        this.leashedEntity = entity;
    }
}
