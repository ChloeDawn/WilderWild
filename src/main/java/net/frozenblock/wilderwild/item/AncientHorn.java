/*
 * Copyright 2023 FrozenBlock
 * This file is part of Wilder Wild.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, see <https://www.gnu.org/licenses/>.
 */

package net.frozenblock.wilderwild.item;

import java.util.Optional;
import net.frozenblock.lib.FrozenMain;
import net.frozenblock.lib.item.impl.CooldownInterface;
import net.frozenblock.lib.sound.api.FrozenSoundPackets;
import net.frozenblock.wilderwild.entity.AncientHornProjectile;
import net.frozenblock.wilderwild.misc.WilderSharedConstants;
import net.frozenblock.wilderwild.registry.RegisterItems;
import net.frozenblock.wilderwild.registry.RegisterSounds;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AncientHorn extends InstrumentItem {
	public static final int DEFAULT_COOLDOWN = 600;
	public static final int CREATIVE_COOLDOWN = 5;
	public static final int SHRIEKER_COOLDOWN = 1200;
	public static final int SENSOR_COOLDOWN = 800;
	public static final int TENDRIL_COOLDOWN = 780;
	public static final int MIN_BUBBLES = 10;
	public static final int MAX_BUBBLES = 25;

	public AncientHorn(@NotNull Properties settings, @NotNull TagKey<Instrument> instruments) {
		super(settings, instruments);
	}

	public static int getCooldown(@Nullable Entity entity, int cooldown) {
		if (entity instanceof Player player && player.isCreative()) {
			return CREATIVE_COOLDOWN;
		}
		return cooldown;
	}

	public static int decreaseCooldown(@NotNull Player user, int time) {
		if (!user.isCreative()) {
			ItemCooldowns manager = user.getCooldowns();
			ItemCooldowns.CooldownInstance entry = manager.cooldowns.get(RegisterItems.ANCIENT_HORN);
			if (entry != null) {
				int between = entry.endTime - entry.startTime;
				if (between > 140 && between >= time) {
					((CooldownInterface) user.getCooldowns()).changeCooldown(RegisterItems.ANCIENT_HORN, -time);
					return time;
				}
			}
		}
		return -1;
	}

	private static void play(@NotNull Level level, @NotNull Player player, @NotNull Instrument instrument) {
		SoundEvent soundEvent = instrument.soundEvent().value();
		float range = instrument.range() / 16F;
		level.playSound(player, player, soundEvent, SoundSource.RECORDS, range, 1.0F);
	}

	@Override
	@NotNull
	public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player user, @NotNull InteractionHand hand) {
		ItemStack itemStack = user.getItemInHand(hand);
		Optional<? extends Holder<Instrument>> optional = this.getInstrument(itemStack);
		if (optional.isPresent()) {
			user.startUsingItem(hand);
			play(level, user, optional.get().value());
			user.getCooldowns().addCooldown(RegisterItems.ANCIENT_HORN, getCooldown(user, DEFAULT_COOLDOWN));
			if (level instanceof ServerLevel server) {
				AncientHornProjectile projectileEntity = new AncientHornProjectile(level, user.getX(), user.getEyeY(), user.getZ());
				projectileEntity.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, 1.0F, 0.0F);
				projectileEntity.setShotByPlayer(true);
				server.addFreshEntity(projectileEntity);
				FrozenSoundPackets.createMovingRestrictionLoopingSound(server, projectileEntity, RegisterSounds.ENTITY_ANCIENT_HORN_PROJECTILE_LOOP, SoundSource.NEUTRAL, 1.0F, 1.0F, FrozenMain.id("default"), true);
				ItemStack mainHand = user.getItemInHand(InteractionHand.MAIN_HAND);
				ItemStack offHand = user.getItemInHand(InteractionHand.OFF_HAND);
				if (mainHand.is(Items.WATER_BUCKET) || mainHand.is(Items.POTION) || offHand.is(Items.WATER_BUCKET) || offHand.is(Items.POTION)) {
					projectileEntity.setBubbles(level.random.nextIntBetweenInclusive(MIN_BUBBLES, MAX_BUBBLES));
				}
			}
			return InteractionResultHolder.consume(itemStack);
		} else {
			WilderSharedConstants.LOGGER.error("Ancient Horn use failed");
			return InteractionResultHolder.fail(itemStack);
		}
	}

	@Override
	public int getUseDuration(@NotNull ItemStack stack) {
		Optional<? extends Holder<Instrument>> optional = this.getInstrument(stack);
		return optional.map(instrumentRegistryEntry -> instrumentRegistryEntry.value().useDuration()).orElse(0);
	}

}
