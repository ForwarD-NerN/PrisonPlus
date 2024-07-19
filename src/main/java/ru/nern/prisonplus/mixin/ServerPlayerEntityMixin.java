package ru.nern.prisonplus.mixin;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.include.com.google.common.collect.Sets;
import ru.nern.prisonplus.PrisonNetworking;
import ru.nern.prisonplus.PrisonPlus;
import ru.nern.prisonplus.structure.Prison;
import ru.nern.prisonplus.structure.PrisonCell;
import ru.nern.prisonplus.structure.PrisonTimeManager;
import ru.nern.prisonplus.utils.ILivingEntityAccessor;
import ru.nern.prisonplus.utils.IPlayerAccessor;
import ru.nern.prisonplus.utils.IWorldPrisonAccessor;
import ru.nern.prisonplus.utils.PrisonUtils;

import java.util.Iterator;
import java.util.Set;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin extends PlayerEntity implements IPlayerAccessor {
    public ServerPlayerEntityMixin(World world, BlockPos pos, float yaw, GameProfile gameProfile) {
        super(world, pos, yaw, gameProfile);
    }
    @Shadow public abstract ServerWorld getServerWorld();

    @Shadow public abstract void sendMessage(Text message);

    @Shadow public abstract void sendMessage(Text message, boolean overlay);

    @Shadow public abstract void requestTeleportAndDismount(double destX, double destY, double destZ);

    @Shadow public abstract void requestTeleport(double destX, double destY, double destZ);
    @Shadow public abstract @Nullable BlockPos getSpawnPointPosition();
    @Shadow @Final public MinecraftServer server;

    @Shadow @Nullable public abstract Entity moveToWorld(ServerWorld destination);

    @Shadow public abstract void teleport(ServerWorld targetWorld, double x, double y, double z, float yaw, float pitch);

    @Shadow public abstract boolean teleport(ServerWorld world, double destX, double destY, double destZ, Set<PositionFlag> flags, float yaw, float pitch);

    private final Set<String> subscribedPrisons = Sets.newHashSet();
    private Prison prison;
    private PrisonCell cell;
    private PrisonTimeManager timeManager;
    private BlockPos preJailPos;
    private Identifier preJailWorld;
    private String reason;
    private BlockPos cachedTeleportPos;
    private int syncTrackingTick = 0;
    private int clientJailSyncTick = 0;
    private boolean isInJail = false;
    @Override
    public void subscribe(String prison) {
        subscribedPrisons.add(prison);
    }

    @Override
    public void unsubscribe(String prison) {
        subscribedPrisons.remove(prison);
    }

    @Override
    public void releaseFromJail() {
        sendMessage(Text.literal("Вы были выпущены из").formatted(Formatting.YELLOW).append(Text.literal(" тюрьмы").formatted(Formatting.GREEN)));
        this.cell.removePlayer(this.getUuid());
        this.isInJail = false;
        this.prison = null;
        this.cell = null;
        this.timeManager = null;
        this.reason = null;
        BlockPos pos = this.preJailPos != null ? this.preJailPos : this.getSpawnPointPosition();

        //Здесь используется метод с set.of как аргумент, т.к нужна проверка тот же ли мир и действие выполняется один раз(нет смысла создавать кучу пакетов на прогрузку чанков)
        teleport(PrisonUtils.getWorld(preJailWorld, server), pos.getX(), pos.getY(), pos.getZ(), Set.of(), getYaw(), getPitch());
        this.preJailPos = null;
    }

    @Override
    public void jail(int time, boolean irl, Prison prison, PrisonCell cell, String reason) {
        ((ILivingEntityAccessor)this).prisonplus$detach(PrisonPlus.config.player.removeHandcuffsWhenPutInPrison);
        sendMessage(Text.literal("Вы были посажены в тюрьму по причине ").formatted(Formatting.YELLOW).append(Text.literal(reason).formatted(Formatting.RED)));
        this.timeManager = new PrisonTimeManager(time, irl);
        this.preJailPos = this.getBlockPos();
        this.preJailWorld = this.getWorld().getDimensionKey().getValue();
        this.isInJail = true;
        this.prison = prison;
        this.cell = cell;
        this.cell.addPlayer(this.getUuid());
        this.reason = reason;
        this.cachedTeleportPos = getTeleportPos(cell.getBounds());
        teleport(PrisonUtils.getWorld(prison.getDimension(), server), cachedTeleportPos.getX(), cachedTeleportPos.getY(), cachedTeleportPos.getZ(), Set.of(), getYaw(), getPitch());

        //Синхронизируем время нахождения в тюрьме клиенту
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(timeManager.isIrl());
        buf.writeInt(timeManager.getTimeLeft());
        ServerPlayNetworking.send((ServerPlayerEntity) (Object)this, PrisonNetworking.TIME_SYNC_PACKET_ID, buf);
    }

    @Inject(method = "startRiding", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;updatePassengerPosition(Lnet/minecraft/entity/Entity;)V", shift = At.Shift.BEFORE), cancellable = true)
    private void prisonplus$disableRiding(Entity entity, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if(isInJail || ((ILivingEntityAccessor)this).prisonplus$isLeashed()) cir.setReturnValue(false);
    }

    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void prisonplus$fallDamageInvulnerable(DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        if(PrisonPlus.config.player.invulnerableToFallDamageWhenInHandcuffs && damageSource.isIn(DamageTypeTags.IS_FALL) && ((ILivingEntityAccessor)this).prisonplus$isLeashed()) cir.setReturnValue(true);
    }


    @Inject(method = "playerTick", at = @At("TAIL"))
    private void prisonplus$trackPrisonsAndPrisoners(CallbackInfo ci) {

        syncTrackingTick++;
        if(syncTrackingTick > PrisonPlus.config.prison.prisonTrackingTime) {
            syncTrackingTick = 0;
            ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
            Iterator<String> iterator = subscribedPrisons.iterator();
            while(iterator.hasNext()) {
                String prisonName = iterator.next();
                IWorldPrisonAccessor accessor = (IWorldPrisonAccessor) getServerWorld();
                if(accessor.hasPrison(prisonName)) {
                    Prison prison = accessor.getPrison(prisonName);
                    if(prison.isDirty()) {
                        //Добавяет тюрьму в список, который в конце тика анмаркает.
                        PrisonPlus.addToList(prison);
                        PrisonNetworking.trackPrison(prison, getServerWorld(), player);
                    }
                }else{
                    PrisonNetworking.unsubscribeFromPrison(prisonName, player, false);
                    iterator.remove();
                }
            }
        }
        if(this.isInJail && this.getWorld() != null) {
            clientJailSyncTick++;
            if(clientJailSyncTick > PrisonPlus.config.prison.clientJailSyncTime) {
                clientJailSyncTick = 0;
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeBoolean(timeManager.isIrl());
                buf.writeInt(timeManager.getTimeLeft());
                ServerPlayNetworking.send((ServerPlayerEntity) (Object)this, PrisonNetworking.TIME_SYNC_PACKET_ID, buf);
            }

            BlockBox boundary = prison.canPrisonersExitCells() ? prison.getBounds() : cell.getBounds();
            if(PrisonUtils.outsideOf(getBlockPos(), boundary)) {
                Vec3d playerPos = getPos();
                BlockPos teleportPos = cachedTeleportPos == null || prison.modified ? cachedTeleportPos = getTeleportPos(cell.getBounds()) : cachedTeleportPos;
                prison.modified = false;
                if(playerPos.x != teleportPos.getX() || playerPos.y != teleportPos.getY() || playerPos.z != teleportPos.getZ()) {
                    if(isInWorld(prison.getDimension())) {
                        requestTeleport(teleportPos.getX()+0.5, teleportPos.getY(), teleportPos.getZ()+0.5);
                    }else{
                        teleport(PrisonUtils.getWorld(prison.getDimension(), server), teleportPos.getX()+0.5, teleportPos.getY()+0.5, teleportPos.getZ()+0.5, getYaw(), getPitch());
                    }
                }
            }
            timeManager.tick();
            if(timeManager.shouldBeFree()) this.releaseFromJail();
        }
    }
    private BlockPos getTeleportPos(BlockBox cellBounds){
        BlockPos center = cellBounds.getCenter();
        double x = center.getX();
        double y = cellBounds.getMinY();
        double z = center.getZ();


        World world = getWorld();
        boolean isLowerBlockFull = false;

        BlockPos.Mutable pos = new BlockPos.Mutable(x, y, z);

        for(int i = (int) y; i < cellBounds.getMaxY()-1; i++) {
            pos.set(x, i, z);
            if(world.getBlockState(pos).isAir()){
                if(isLowerBlockFull) {
                    if(world.getBlockState(pos.down()).isAir()) world.setBlockState(pos.down(), Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);
                    world.setBlockState(pos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    return pos;
                }
            }else{
                isLowerBlockFull = true;
            }
        }
        pos.set(x, y, z);
        if(world.getBlockState(pos).isAir()) world.setBlockState(pos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(pos.up(1), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(pos.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        return pos.up();

    }

    //Восстанавливает данные после смерти
    @Inject(method = "copyFrom", at = @At("TAIL"))
    public void prisonplus$attachDataOnDeath(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
        IPlayerAccessor old = (IPlayerAccessor) oldPlayer;
        this.subscribedPrisons.addAll(old.getSubscribedPrisons());
        if(old.isInJail()) {
            this.timeManager = old.getTimeManager();
            this.preJailPos = old.getPreJailPos();
            this.preJailWorld = old.getPreJailWorld();
            this.isInJail = old.isInJail();
            this.prison = old.getPrison();
            this.cell = old.getCell();
            this.reason = old.getReason();
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void prisonplus$writeCustomDataToNbt(NbtCompound nbt, CallbackInfo ci) {
        if(this.prison != null && this.cell != null) {
            nbt.putString("PrisonName", prison.getName());
            nbt.putString("PrisonCellName", cell.getName());
            nbt.putString("PrisonDimension", prison.getDimension().toString());
            if(preJailPos != null) nbt.putLong("PrisonPreJailPos", preJailPos.asLong());
            if(preJailWorld != null) nbt.putString("PrisonPreJailWorld", preJailWorld.toString());

            //Serializing time manager
            if(this.timeManager != null) {
                NbtCompound timeManager = new NbtCompound();
                timeManager.putBoolean("IsIrl", this.timeManager.isIrl());
                timeManager.putInt("TimeLeft", this.timeManager.getTimeLeft());
                timeManager.putLong("Timestamp", PrisonUtils.getUnixTimestamp());
                nbt.put("PrisonTimeManager", timeManager);
            }
            if(this.reason != null) nbt.putString("PrisonJailReason", this.reason);
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void prisonplus$readCustomDataFromNbt(NbtCompound nbt, CallbackInfo ci) {
        if(nbt.contains("PrisonName")) {
            try {
                String dimension = nbt.getString("PrisonDimension");
                if (Identifier.isValid(dimension)) {
                    Identifier identifier = Identifier.tryParse(dimension);
                    RegistryKey<World> registryKey = RegistryKey.of(RegistryKeys.WORLD, identifier);
                    ServerWorld world = getServerWorld().getServer().getWorld(registryKey);
                    if (world != null) {
                        this.prison = PrisonUtils.getPrisonFromWorld(world, nbt.getString("PrisonName"));
                        if (this.prison != null) {
                            if (nbt.contains("PrisonCellName") && prison.hasCell(nbt.getString("PrisonCellName"))) {
                                NbtCompound timeManager = nbt.getCompound("PrisonTimeManager");
                                boolean isIrl = timeManager.getBoolean("IsIrl");
                                int timeLeft = timeManager.getInt("TimeLeft") / 20;
                                long timestamp = timeManager.getLong("Timestamp");

                                this.cell = prison.getCell(nbt.getString("PrisonCellName"));
                                this.cell.addPlayer(this.getUuid());
                                if(nbt.contains("PrisonPreJailPos")) this.preJailPos = BlockPos.fromLong(nbt.getLong("PrisonPreJailPos"));
                                if(nbt.contains("PrisonPreJailWorld")) this.preJailWorld = Identifier.tryParse(nbt.getString("PrisonPreJailWorld"));
                                if(nbt.contains("PrisonJailReason")) this.reason = nbt.getString("PrisonJailReason");

                                //Если счёт идёт через irl время, то передаём его, если нет, то передаём оставшиеся тики.
                                this.timeManager = new PrisonTimeManager(isIrl ? (int) (timeLeft - (PrisonUtils.getUnixTimestamp() - timestamp)) * 20 : timeLeft * 20, isIrl);
                                this.isInJail = true;
                            } else {
                                PrisonPlus.LOGGER.info("Player {} was released from the prison. Prison Cell wasn't found.", getName().toString());
                            }
                        } else {
                            PrisonPlus.LOGGER.info("Player {} was released from the prison. Prison wasn't found.", getName().toString());
                        }

                    }
                } else {
                    PrisonPlus.LOGGER.error("Invalid dimension identifier");
                }
            }catch (Exception exception){
                PrisonPlus.LOGGER.error("Error occurred when parsing player prison data: " + exception);
            }
        }
    }

    @Override public boolean subscribedTo(String prisonName) {
        return this.subscribedPrisons.contains(prisonName);
    }
    @Override public boolean isInJail() {
        return this.isInJail;
    }
    @Override public Prison getPrison() {
        return this.prison;
    }
    @Override public PrisonCell getCell() {
        return this.cell;
    }
    @Override public String getReason() {
        return reason;
    }
    @Override public BlockPos getPreJailPos() {
        return preJailPos;
    }
    @Override public Identifier getPreJailWorld() {
        return preJailWorld;
    }
    @Override public PrisonTimeManager getTimeManager() {
        return timeManager;
    }

    @Override
    public Set<String> getSubscribedPrisons() {
        return subscribedPrisons;
    }

    @Unique
    private boolean isInWorld(Identifier identifier) {
        return getServerWorld().getDimensionKey().getValue().toString().equals(identifier.toString());
    }
}
