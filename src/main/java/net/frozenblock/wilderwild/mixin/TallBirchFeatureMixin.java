package net.frozenblock.wilderwild.mixin;

import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.gen.feature.DefaultBiomeFeatures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(DefaultBiomeFeatures.class)
public class TallBirchFeatureMixin {

    //DELETE THIS IF NO LONGER NEEDED

    /**
     * @author FrozenBlock
     * @reason new birch pog
     */
    @Overwrite
    public static void addTallBirchTrees(GenerationSettings.Builder builder) {
    }
}
