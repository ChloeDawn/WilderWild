/*
 * Copyright 2022 QuiltMC
 * Modified to work on Fabric
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.qsl.frozenblock.datafixerupper.impl;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.schemas.Schema;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import org.jetbrains.annotations.*;

/**
 * Modified to work on Fabric
 */
@ApiStatus.Internal
public abstract class QuiltDataFixesInternals {
    public record DataFixerEntry(DataFixer dataFixer, int currentVersion) {
    }

    @Contract(pure = true)
    @Range(from = 0, to = Integer.MAX_VALUE)
    public static int getModDataVersion(@NotNull CompoundTag compound, @NotNull String modId) {
        return compound.getInt(modId + "_DataVersion");
    }

    private static QuiltDataFixesInternals instance;

    public static @NotNull QuiltDataFixesInternals get() {
        if (instance == null) {
            Schema latestVanillaSchema;
            try {
                latestVanillaSchema = DataFixers.getDataFixer()
                        .getSchema(DataFixUtils.makeKey(SharedConstants.getCurrentVersion().getDataVersion().getVersion()));
            } catch (Exception e) {
                latestVanillaSchema = null;
            }

            if (latestVanillaSchema == null) {
                // either this Minecraft version is horribly broken, or someone is suppressing DFU initialization
                // use a no-op impl
                instance = new NopQuiltDataFixesInternals();
            } else {
                instance = new QuiltDataFixesInternalsImpl(latestVanillaSchema);
            }
        }

        return instance;
    }

    public abstract void registerFixer(@NotNull String modId,
                                       @Range(from = 0, to = Integer.MAX_VALUE) int currentVersion,
                                       @NotNull DataFixer dataFixer);

    public abstract @Nullable DataFixerEntry getFixerEntry(@NotNull String modId);

    @Contract(value = "-> new", pure = true)
    public abstract @NotNull Schema createBaseSchema();

    public abstract @NotNull CompoundTag updateWithAllFixers(@NotNull DataFixTypes dataFixTypes,
                                                             @NotNull CompoundTag compound);

    @Contract("_ -> new")
    public abstract @NotNull CompoundTag addModDataVersions(@NotNull CompoundTag compound);

    public abstract void freeze();

    @Contract(pure = true)
    public abstract boolean isFrozen();
}