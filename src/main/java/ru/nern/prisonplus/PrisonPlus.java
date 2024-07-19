package ru.nern.prisonplus;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.nern.prisonplus.commands.PrisonCommands;
import ru.nern.prisonplus.integration.PrisonPlusEventListenerSelector;
import ru.nern.prisonplus.structure.Prison;
import ru.nern.prisonplus.structure.PrisonItems;
import ru.nern.prisonplus.structure.PrisonLeashEntity;
import ru.nern.prisonplus.structure.enums.AllowedInteraction;
import ru.nern.prisonplus.structure.enums.PlayerGroup;
import ru.nern.prisonplus.utils.ILivingEntityAccessor;
import ru.nern.prisonplus.utils.IPlayerAccessor;
import ru.nern.prisonplus.utils.PrisonUtils;
import xyz.nucleoid.stimuli.Stimuli;
import xyz.nucleoid.stimuli.event.block.BlockBreakEvent;
import xyz.nucleoid.stimuli.event.block.BlockPlaceEvent;
import xyz.nucleoid.stimuli.event.block.BlockUseEvent;
import xyz.nucleoid.stimuli.event.block.FluidPlaceEvent;
import xyz.nucleoid.stimuli.event.entity.EntityDamageEvent;
import xyz.nucleoid.stimuli.event.entity.EntityUseEvent;
import xyz.nucleoid.stimuli.event.item.ItemCraftEvent;
import xyz.nucleoid.stimuli.event.item.ItemPickupEvent;
import xyz.nucleoid.stimuli.event.item.ItemThrowEvent;
import xyz.nucleoid.stimuli.event.item.ItemUseEvent;
import xyz.nucleoid.stimuli.event.player.PlayerInventoryActionEvent;

import java.util.HashMap;
import java.util.Set;


public class PrisonPlus implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("prisonplus");
	public static final String MOD_VERSION = "1.0.0";
	//Существует только на клиенте. Тюрьмы, которые в нём находятся, рендерятся на клиенте.
	public static final HashMap<String, Prison> RENDERED_PRISONS = Maps.newHashMap();
	public static final Text STYLED_MOD_NAME = Text.Serializer.fromJson("[{\"text\":\"=\",\"color\":\"red\"},{\"text\":\"=\",\"color\":\"#f33f49\"},{\"text\":\"=\",\"color\":\"#e93f53\"},{\"text\":\"=\",\"color\":\"#df3e5d\"},{\"text\":\"=\",\"color\":\"#d53f67\"},{\"text\":\"P\",\"color\":\"#cb3f71\"},{\"text\":\"r\",\"color\":\"#c13f7b\"},{\"text\":\"i\",\"color\":\"#b73f85\"},{\"text\":\"s\",\"color\":\"#ad3f8f\"},{\"text\":\"o\",\"color\":\"#a33f99\"},{\"text\":\"n\",\"color\":\"#993fa3\"},{\"text\":\"P\",\"color\":\"#8f3fad\"},{\"text\":\"l\",\"color\":\"#853fb7\"},{\"text\":\"u\",\"color\":\"#7b3fc1\"},{\"text\":\"s\",\"color\":\"#713fcb\"},{\"text\":\"=\",\"color\":\"#673fd5\"},{\"text\":\"=\",\"color\":\"#5d3fdf\"},{\"text\":\"=\",\"color\":\"#533fe9\"},{\"text\":\"=\",\"color\":\"#493ff3\"},{\"text\":\"=\",\"color\":\"blue\"}]");
	private static final Set<Prison> scheduledPrisons = Sets.newHashSet();
	public static ConfigurationManager.Config config = new ConfigurationManager.Config();

	@Override
	public void onInitialize()
	{
		ConfigurationManager.onInit();
		ServerLifecycleEvents.SERVER_STOPPING.register((PrisonUtils::savePrisonData));
		ServerLifecycleEvents.SERVER_STARTED.register((PrisonUtils::loadPrisonData));

		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			ItemStack stack = player.getStackInHand(hand);
			if(world.isClient || !(entity instanceof LivingEntity livingEntity) || hand == Hand.OFF_HAND) return ActionResult.PASS;
			ServerWorld serverWorld = (ServerWorld) world;

			ILivingEntityAccessor entityAccessor = (ILivingEntityAccessor) entity;

			if(((ILivingEntityAccessor)player).prisonplus$isLeashed()) return ActionResult.FAIL;
			ItemCooldownManager cooldownManager = player.getItemCooldownManager();

			boolean scissors = PrisonPlus.config.items.scissorsEnabled && stack.hasNbt() && stack.getNbt().contains("Scissors") && Permissions.check(player, "prisonplus.use_scissors", true);
			boolean handcuffs = stack.isOf(Items.LEAD) && stack.hasNbt() && stack.getNbt().contains("Handcuffs") && Permissions.check(player, "prisonplus.take_off_handcuffs", true) && !cooldownManager.isCoolingDown(Items.LEAD);
			if(entityAccessor.prisonplus$isLeashed() && (scissors || handcuffs)){
				if(scissors){
					world.playSound(null, entity.getBlockPos(), SoundEvents.ENTITY_MOOSHROOM_SHEAR, SoundCategory.PLAYERS, 20, 0.5f);
					if(!player.getAbilities().creativeMode)stack.damage(1, player, e -> e.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND));
					cooldownManager.set(Items.SHEARS, PrisonPlus.config.items.scissorsCooldown);
				}else{
					cooldownManager.set(Items.LEAD, PrisonPlus.config.items.handcuffsCooldown);
					world.playSound(null, entity.getBlockPos(), SoundEvents.ENTITY_LEASH_KNOT_BREAK, SoundCategory.PLAYERS, 50, 0.5f);
				}
				entityAccessor.prisonplus$detach(true);
				return ActionResult.SUCCESS;
			}
			if (stack.hasNbt() && stack.isOf(Items.LEAD) && Permissions.check(player, "prisonplus.use_handcuffs", true) && !cooldownManager.isCoolingDown(Items.LEAD)){
				if(livingEntity.hasStatusEffect(StatusEffects.BLINDNESS) && livingEntity.hasStatusEffect(StatusEffects.SLOWNESS)){
					cooldownManager.set(Items.LEAD, PrisonPlus.config.items.handcuffsCooldown);

					if(entityAccessor.prisonplus$isLeashed()) return ActionResult.FAIL;

					PrisonLeashEntity leashEntity = new PrisonLeashEntity(serverWorld, livingEntity);
					leashEntity.setSilent(true);
					leashEntity.setInvulnerable(true);
					leashEntity.setHealth(0);
					leashEntity.setBaby(true);
					leashEntity.setLeashedEntity(entity);
					leashEntity.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, -1, 1, true, false));
					serverWorld.spawnEntity(leashEntity);
					leashEntity.refreshPositionAndAngles(livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(), 0f, 0f);
					entityAccessor.prisonplus$setLeashed(leashEntity);

					if(PrisonPlus.config.items.consumeHandcuffs && !player.getAbilities().creativeMode) stack.decrement(1);
					if(entity.hasVehicle()) entity.stopRiding();

					world.playSound(null, entity.getBlockPos(), SoundEvents.ENTITY_LEASH_KNOT_PLACE, SoundCategory.PLAYERS, 50, 0.5f);
					leashEntity.attachLeash(player, true);

					return ActionResult.SUCCESS;
				}
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});

		EntityElytraEvents.ALLOW.register(entity -> !entity.hasStatusEffect(StatusEffects.SLOWNESS) || !entity.hasStatusEffect(StatusEffects.BLINDNESS) || !entity.hasStatusEffect(StatusEffects.WEAKNESS));
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> !((ILivingEntityAccessor) player).prisonplus$isLeashed());
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->{
			if(world.isClient) return ActionResult.PASS;
			return ((ILivingEntityAccessor)player).prisonplus$isLeashed() ? ActionResult.FAIL : ActionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			if(world.isClient) return TypedActionResult.pass(stack);
            return ((ILivingEntityAccessor)player).prisonplus$isLeashed() ? TypedActionResult.fail(stack) : TypedActionResult.pass(stack);
        });

		AttackEntityCallback.EVENT.register(((player, world, hand, entity, hitResult) -> {
			ItemStack stack = player.getStackInHand(hand);
			if(world.isClient || !(entity instanceof LivingEntity livingEntity)) return ActionResult.PASS;
			ServerWorld serverWorld = (ServerWorld) world;

			if(((ILivingEntityAccessor)player).prisonplus$isLeashed()) return ActionResult.FAIL;
			if(!stack.hasNbt()) return ActionResult.PASS;

			if(stack.isOf(Items.STICK) && Permissions.check(player, "prisonplus.use_baton", true) && PrisonItems.isBaton(stack) && !player.getItemCooldownManager().isCoolingDown(Items.STICK)){
				player.getItemCooldownManager().set(Items.STICK, PrisonPlus.config.items.batonCooldown);
				serverWorld.spawnParticles(ParticleTypes.ANGRY_VILLAGER, entity.getX(), entity.getY(), entity.getZ(), 4, 0.5, 0.5, 0.5, 0.1);
				world.playSound(null, entity.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 5, 0.3f);
				livingEntity.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, PrisonPlus.config.items.blindnessDurationBaton*20, PrisonPlus.config.items.blindnessLevelBaton));
				livingEntity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, PrisonPlus.config.items.slownessDurationBaton*20, PrisonPlus.config.items.slownessLevelBaton));
				livingEntity.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, PrisonPlus.config.items.weaknessDurationBaton*20, PrisonPlus.config.items.weaknessLevelBaton));
				return ActionResult.SUCCESS;
			}

			return ActionResult.PASS;
		}));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			ILivingEntityAccessor old = (ILivingEntityAccessor) oldPlayer;
			if(old.prisonplus$isLeashed()) {
				((ILivingEntityAccessor)newPlayer).prisonplus$setLeashed(null);
			}
		});

		Stimuli.global().listen(PlayerInventoryActionEvent.EVENT, ((player, slot, actionType, button) -> {
			if(slot < 0 || slot > 100) return ActionResult.PASS;

			ItemStack stack = player.getInventory().getStack(slot);
			boolean isPlayerLeashed = ((ILivingEntityAccessor)player).prisonplus$isLeashed();
			if(stack.hasNbt() && stack.getNbt().contains("HandcuffsChestplate")){
				if(isPlayerLeashed) return ActionResult.FAIL;
				player.getInventory().setStack(slot, ItemStack.EMPTY);
			}
			return ActionResult.PASS;
		}));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			IPlayerAccessor accessor = (IPlayerAccessor) player;
			if(accessor.isInJail()) {
				int timeLeft = accessor.getTimeManager().getTimeLeft();
				if(timeLeft > 0) {
					PacketByteBuf buf = PacketByteBufs.create();
					buf.writeBoolean(accessor.getTimeManager().isIrl());
					buf.writeInt(timeLeft);
					ServerPlayNetworking.send(player, PrisonNetworking.TIME_SYNC_PACKET_ID, buf);
				}
			}
		});

		ServerTickEvents.END_SERVER_TICK.register((server -> scheduledPrisons.forEach(PrisonPlus::removeFromList)));
		PrisonCommands.init();
		Stimuli.registerSelector(new PrisonPlusEventListenerSelector());
	}

	public static void addToList(Prison prison) {
		scheduledPrisons.add(prison);
	}

	public static void removeFromList(Prison prison) {
		prison.unmarkDirty();
		scheduledPrisons.remove(prison);
	}

	public static void listenToEvents(Prison prison) {

		prison.getEventListeners().listen(BlockUseEvent.EVENT, (player, hand, hitResult) -> {
            PlayerGroup group = ((IPlayerAccessor) player).isInJail() ? PlayerGroup.PRISONERS : PlayerGroup.EVERYONE;
            return !prison.getAllowedInteractions(group).contains(AllowedInteraction.USE_BLOCKS) ? ActionResult.FAIL : ActionResult.PASS;
        });

		prison.getEventListeners().listen(ItemUseEvent.EVENT, (player, hand) -> {
            PlayerGroup group = ((IPlayerAccessor) player).isInJail() ? PlayerGroup.PRISONERS : PlayerGroup.EVERYONE;
            return !prison.getAllowedInteractions(group).contains(AllowedInteraction.USE_ITEMS) ? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand));
        });

		prison.getEventListeners().listen(BlockPlaceEvent.BEFORE, (player, world, pos, state, context) -> {
			PlayerGroup group = ((IPlayerAccessor) player).isInJail() ? PlayerGroup.PRISONERS : PlayerGroup.EVERYONE;
			return !prison.getAllowedInteractions(group).contains(AllowedInteraction.PLACE_BLOCKS) ? ActionResult.FAIL : ActionResult.PASS;
		});

		prison.getEventListeners().listen(FluidPlaceEvent.EVENT, ((world, pos, player, hitResult) -> {
			if(player != null) {
				PlayerGroup group = ((IPlayerAccessor) player).isInJail() ? PlayerGroup.PRISONERS : PlayerGroup.EVERYONE;
				return !prison.getAllowedInteractions(group).contains(AllowedInteraction.PLACE_FLUIDS) ? ActionResult.FAIL : ActionResult.PASS;
			}
			return ActionResult.PASS;
		}));

		prison.getEventListeners().listen(BlockBreakEvent.EVENT, (player, world, pos) -> {
			PlayerGroup group = ((IPlayerAccessor) player).isInJail() ? PlayerGroup.PRISONERS : PlayerGroup.EVERYONE;
			return !prison.getAllowedInteractions(group).contains(AllowedInteraction.BREAK_BLOCK) ? ActionResult.FAIL : ActionResult.PASS;
		});

		prison.getEventListeners().listen(EntityDamageEvent.EVENT, (entity, source, amount) -> {
			if(!(source.getAttacker() instanceof PlayerEntity)) return ActionResult.PASS;

			PlayerGroup group = ((IPlayerAccessor) source.getAttacker()).isInJail() ? PlayerGroup.PRISONERS : PlayerGroup.EVERYONE;
			return !prison.getAllowedInteractions(group).contains(AllowedInteraction.HIT_ENTITIES) ? ActionResult.FAIL : ActionResult.PASS;
		});

		prison.getEventListeners().listen(EntityUseEvent.EVENT, (player, entity, hand, hitResult) -> {
			PlayerGroup group = ((IPlayerAccessor) player).isInJail() ? PlayerGroup.PRISONERS : PlayerGroup.EVERYONE;
			return !prison.getAllowedInteractions(group).contains(AllowedInteraction.INTERACT_ENTITIES) ? ActionResult.FAIL : ActionResult.PASS;
		});

		prison.getEventListeners().listen(ItemCraftEvent.EVENT, ((player, recipe) -> {
			PlayerGroup group = ((IPlayerAccessor) player).isInJail() ? PlayerGroup.PRISONERS : PlayerGroup.EVERYONE;
			return !prison.getAllowedInteractions(group).contains(AllowedInteraction.CRAFT_ITEMS) ? ActionResult.FAIL : ActionResult.PASS;
		}));

		prison.getEventListeners().listen(ItemPickupEvent.EVENT, (player, entity, stack) -> {
			PlayerGroup group = ((IPlayerAccessor) player).isInJail() ? PlayerGroup.PRISONERS : PlayerGroup.EVERYONE;
			return !prison.getAllowedInteractions(group).contains(AllowedInteraction.PICKUP_ITEMS) ? ActionResult.FAIL : ActionResult.PASS;
		});

		prison.getEventListeners().listen(ItemThrowEvent.EVENT, (player, entity, stack) -> {
			PlayerGroup group = ((IPlayerAccessor) player).isInJail() ? PlayerGroup.PRISONERS : PlayerGroup.EVERYONE;
			return !prison.getAllowedInteractions(group).contains(AllowedInteraction.THROW_ITEMS) ? ActionResult.FAIL : ActionResult.PASS;
		});
	}
}
