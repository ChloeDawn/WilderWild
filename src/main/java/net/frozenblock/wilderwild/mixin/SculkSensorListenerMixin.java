package net.frozenblock.wilderwild.mixin;

import net.frozenblock.wilderwild.entity.SculkSensorTendrilEntity;
import net.frozenblock.wilderwild.registry.RegisterEntities;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.listener.SculkSensorListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

@Mixin(SculkSensorListener.class)
public class SculkSensorListenerMixin {

    @Inject(at = @At("TAIL"), method = "tick")
    public void tick(World world, CallbackInfo info) {
        SculkSensorListener listener = SculkSensorListener.class.cast(this);
        if (world instanceof ServerWorld server) {
            Optional<Vec3d> pos = listener.getPositionSource().getPos(server);
            if (pos.isPresent()) {
                BlockPos blockPos = new BlockPos(pos.get());
                if (world.getBlockState(blockPos).isOf(Blocks.SCULK_SENSOR)) {
                    Box box = (new Box(blockPos.add(0, 0, 0), blockPos.add(1, 1, 1)));
                    List<SculkSensorTendrilEntity> list = world.getNonSpectatingEntities(SculkSensorTendrilEntity.class, box);
                    if (list.isEmpty()) {
                        SculkSensorTendrilEntity tendrils = RegisterEntities.TENDRIL_ENTITY.create(server);
                        assert tendrils != null;
                        tendrils.refreshPositionAndAngles(blockPos.getX() + 0.5D, blockPos.getY()+0.5D, blockPos.getZ() + 0.5D, 0.0F, 0.0F);
                        server.spawnEntity(tendrils);
                    }
                }
            }
        }
    }

}