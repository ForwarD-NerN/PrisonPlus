package ru.nern.prisonplus.mixin;

import com.google.common.collect.Maps;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.nern.prisonplus.commands.PrisonCommands;
import ru.nern.prisonplus.structure.Prison;
import ru.nern.prisonplus.structure.enums.AllowedInteraction;
import ru.nern.prisonplus.structure.enums.PlayerGroup;
import ru.nern.prisonplus.utils.IPlayerAccessor;
import ru.nern.prisonplus.utils.IWorldPrisonAccessor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Mixin(ServerWorld.class)
public class ServerWorldMixin implements IWorldPrisonAccessor {
	@Unique
	private final HashMap<String, Prison> prisons = Maps.newHashMap();

	@Unique
	@Override
	public HashMap<String, Prison> getPrisonsInDimension() {
		return prisons;
	}

	@Override
	public void newPrison(Prison prison) {
		prisons.put(prison.getName(), prison);
	}

	@Override
	public int getPrisonAmount() {
		return prisons.size();
	}

	@Override
	public Iterator<Prison> getPrisonIterator() {
		return prisons.values().iterator();
	}

	@Override
	public boolean hasIntersections(BlockBox box) {
        for (Prison prison : prisons.values()) {
            if (prison.getBounds().intersects(box)) return true;
        }
		return false;
	}

	/*
	@Override
	public boolean isAccessRestricted(BlockPos pos, PlayerEntity player) {

		return false;
	}

	 */

	@Override
	public boolean hasPrison(String key) {
		return prisons.containsKey(key);
	}

	@Override
	public void putPrisonData(Map<String, Prison> data) {
		prisons.putAll(data);
	}

	@Override
	public Prison getPrison(String key) {
		return prisons.get(key);
	}

	@Override
	public Prison getPrisonOrThrow(String key) throws CommandSyntaxException {
		Prison prison = prisons.get(key);
		if(prison == null) throw PrisonCommands.PRISON_NOT_FOUND_ERROR.create();
		return prison;
	}

	@Override
	public void removePrison(String key) {
		prisons.remove(key);
	}

	/*
	@Inject(method = "canPlayerModifyAt", at = @At("HEAD"), cancellable = true)
	public void prisonPlus$restrictPrisonBreaking(PlayerEntity player, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (isAccessRestricted(pos, player)) cir.setReturnValue(false);
	}

	 */
}
