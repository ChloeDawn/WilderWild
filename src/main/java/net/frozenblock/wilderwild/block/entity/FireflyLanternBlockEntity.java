package net.frozenblock.wilderwild.block.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.frozenblock.wilderwild.WilderWild;
import net.frozenblock.wilderwild.entity.Firefly;
import net.frozenblock.wilderwild.entity.ai.FireflyBrain;
import net.frozenblock.wilderwild.item.FireflyBottle;
import net.frozenblock.wilderwild.misc.ClientMethodInteractionThingy;
import net.frozenblock.wilderwild.registry.RegisterBlockEntities;
import net.frozenblock.wilderwild.registry.RegisterEntities;
import net.frozenblock.wilderwild.registry.RegisterSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class FireflyLanternBlockEntity extends BlockEntity {
    public NonNullList<ItemStack> inventory;
    ArrayList<FireflyInLantern> fireflies = new ArrayList<>();

    public int age;
    public boolean hasUpdated = false;

    public FireflyLanternBlockEntity(BlockPos pos, BlockState state) {
        super(RegisterBlockEntities.FIREFLY_LANTERN, pos, state);
        this.inventory = NonNullList.withSize(1, ItemStack.EMPTY);
    }

    public void serverTick(Level world, BlockPos pos) {
        if (!this.fireflies.isEmpty()) {
            for (FireflyInLantern firefly : this.fireflies) {
                firefly.tick(world, pos);
            }
        }
    }

    public void clientTick(Level world, BlockPos pos) {
        if (world.isClientSide) {
            if (!this.hasUpdated) {
                this.hasUpdated = true;
                ClientMethodInteractionThingy.requestBlockEntitySync(pos, world);
            }
        }
        this.age += 1;
        if (!this.fireflies.isEmpty()) {
            for (FireflyInLantern firefly : this.fireflies) {
                firefly.tick(world, pos);
            }
        }
    }

    public void updatePlayer(ServerPlayer player) {
        player.connection.send(this.getUpdatePacket());
    }

    public void updateSync() {
        for (ServerPlayer player : PlayerLookup.tracking(this)) {
            player.connection.send(this.getUpdatePacket());
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    public boolean invEmpty() {
        Optional<ItemStack> stack1 = this.inventory.stream().findFirst();
        return stack1.map(itemStack -> itemStack == ItemStack.EMPTY).orElse(true);
    }

    public Optional<ItemStack> getItem() {
        return this.inventory.stream().findFirst();
    }

    public boolean noFireflies() {
        return this.getFireflies().isEmpty();
    }

    public void load(CompoundTag nbt) {
        super.load(nbt);
        if (nbt.contains("Fireflies", 9)) {
            this.fireflies.clear();
            DataResult<?> var10000 = FireflyInLantern.CODEC.listOf().parse(new Dynamic<>(NbtOps.INSTANCE, nbt.getList("Fireflies", 10)));
            Logger var10001 = WilderWild.LOGGER;
            Objects.requireNonNull(var10001);
            Optional<List> list = (Optional<List>) var10000.resultOrPartial(var10001::error);
            if (list.isPresent()) {
                List<?> fireflyList = list.get();
                for (Object o : fireflyList) {
                    this.fireflies.add((FireflyInLantern) o);
                }
            }
        }
        this.inventory = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(nbt, this.inventory);
        this.age = nbt.getInt("age");
    }

    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        DataResult<?> var10000 = FireflyInLantern.CODEC.listOf().encodeStart(NbtOps.INSTANCE, this.fireflies);
        Logger var10001 = WilderWild.LOGGER;
        Objects.requireNonNull(var10001);
        var10000.resultOrPartial(var10001::error).ifPresent((cursorsNbt) -> {
            nbt.put("Fireflies", (Tag) cursorsNbt);
        });
        ContainerHelper.saveAllItems(nbt, this.inventory);
        nbt.putInt("age", this.age);
    }

    public ArrayList<FireflyInLantern> getFireflies() {
        return this.fireflies;
    }

    public void addFirefly(FireflyBottle bottle, String name) {
        Vec3 newVec = new Vec3(0.5 + (0.15 - Math.random() * 0.3), 0, 0.5 + (0.15 - Math.random() * 0.3));
        this.fireflies.add(new FireflyInLantern(newVec, bottle.color, name, Math.random() > 0.7, (int) (Math.random() * 20), 0));
    }

    public void removeFirefly(FireflyInLantern firefly) {
        this.fireflies.remove(firefly);
    }

    public void spawnFireflies() {
        if (this.level != null) {
            if (!this.level.isClientSide) {
                double extraHeight = this.getBlockState().getValue(BlockStateProperties.HANGING) ? 0.155 : 0;
                for (FireflyLanternBlockEntity.FireflyInLantern firefly : this.getFireflies()) {
                    Firefly entity = RegisterEntities.FIREFLY.create(level);
                    if (entity != null) {
                        entity.moveTo(worldPosition.getX() + firefly.pos.x, worldPosition.getY() + firefly.y + extraHeight + 0.07, worldPosition.getZ() + firefly.pos.z, 0, 0);
                        entity.setFromBottle(true);
                        boolean spawned = level.addFreshEntity(entity);
                        if (spawned) {
                            entity.hasHome = true;
                            FireflyBrain.rememberHome(entity, entity.blockPosition());
                            entity.setColor(firefly.color);
                            entity.setScale(1.0F);
                            if (!Objects.equals(firefly.customName, "")) {
                                entity.setCustomName(Component.nullToEmpty(firefly.customName));
                            }
                        } else {
                            WilderWild.log("Couldn't spawn Firefly from lantern @ " + worldPosition, WilderWild.UNSTABLE_LOGGING);
                        }
                    }
                }
            }
        }
    }

    public void spawnFireflies(Level world) {
        double extraHeight = this.getBlockState().getValue(BlockStateProperties.HANGING) ? 0.155 : 0;
        for (FireflyLanternBlockEntity.FireflyInLantern firefly : this.getFireflies()) {
            Firefly entity = RegisterEntities.FIREFLY.create(world);
            if (entity != null) {
                entity.moveTo(worldPosition.getX() + firefly.pos.x, worldPosition.getY() + firefly.y + extraHeight + 0.07, worldPosition.getZ() + firefly.pos.z, 0, 0);
                entity.setFromBottle(true);
                boolean spawned = world.addFreshEntity(entity);
                if (spawned) {
                    entity.hasHome = true;
                    FireflyBrain.rememberHome(entity, entity.blockPosition());
                    entity.setColor(firefly.color);
                    entity.setScale(1.0F);
                    if (!Objects.equals(firefly.customName, "")) {
                        entity.setCustomName(Component.nullToEmpty(firefly.customName));
                    }
                } else {
                    WilderWild.log("Couldn't spawn Firefly from lantern @ " + worldPosition, WilderWild.UNSTABLE_LOGGING);
                }
            }
        }
    }

    public static class FireflyInLantern {
        public Vec3 pos;
        public String color;
        public String customName;
        public boolean flickers;
        public int age;
        public double y;
        public boolean wasNamedNectar;

        public static final Codec<FireflyInLantern> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
                Vec3.CODEC.fieldOf("pos").forGetter(FireflyInLantern::getPos),
                Codec.STRING.fieldOf("color").forGetter(FireflyInLantern::getColor),
                Codec.STRING.fieldOf("customName").orElse("").forGetter(FireflyInLantern::getCustomName),
                Codec.BOOL.fieldOf("flickers").orElse(false).forGetter(FireflyInLantern::getFlickers),
                Codec.INT.fieldOf("age").forGetter(FireflyInLantern::getAge),
                Codec.DOUBLE.fieldOf("y").forGetter(FireflyInLantern::getY)
        ).apply(instance, FireflyInLantern::new));

        public FireflyInLantern(Vec3 pos, String color, String customName, boolean flickers, int age, double y) {
            this.pos = pos;
            this.color = color;
            this.customName = customName;
            this.flickers = flickers;
            this.age = age;
            this.y = y;
        }

        boolean nectar = false;

        public void tick(Level world, BlockPos pos) {
            this.age += 1;
            this.y = Math.sin(this.age * 0.03) * 0.15;
            nectar = this.getCustomName().toLowerCase().contains("nectar");

            if (nectar != wasNamedNectar) {
                if (nectar) {
                    if (world.getGameTime() % 70L == 0L) {
                        world.playSound(null, pos, RegisterSounds.BLOCK_FIREFLY_LANTERN_NECTAR_LOOP, SoundSource.AMBIENT, 0.5F, 1.0F);
                    }
                    this.wasNamedNectar = true;
                } else {
                    this.wasNamedNectar = false;
                }
            } else {
                this.wasNamedNectar = false;
            }
        }

        public Vec3 getPos() {
            return this.pos;
        }

        public String getColor() {
            return this.color;
        }

        public String getCustomName() {
            return this.customName;
        }

        public boolean getFlickers() {
            return this.flickers;
        }

        public int getAge() {
            return this.age;
        }

        public double getY() {
            return this.y;
        }

    }

}