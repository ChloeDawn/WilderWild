package net.frozenblock.wilderwild.mixin.client.clouds;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import net.frozenblock.lib.wind.api.ClientWindManager;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class CloudRendererMixin {

	@Unique
	private float wilderWild$cloudHeight;

	@ModifyVariable(method = "renderClouds", at = @At(value = "STORE"), ordinal = 1)
	private float getCloudHeight(float original) {
		this.wilderWild$cloudHeight = original;
		return original;
	}

	@ModifyVariable(method = "renderClouds", at = @At(value = "STORE"), ordinal = 5)
	private double modifyX(double original, PoseStack poseStack, Matrix4f projectionMatrix, float partialTick, double camX) {
		return (camX / 12.0) - ClientWindManager.getCloudX(partialTick);
	}

	@ModifyVariable(method = "renderClouds", at = @At("STORE"), ordinal = 6)
	private double modifyY(double original, PoseStack poseStack, Matrix4f projectionMatrix, float partialTick, double camX, double camY) {
		return (double)(this.wilderWild$cloudHeight - (float)camY + 0.33F) + Mth.clamp(ClientWindManager.getCloudY(partialTick), -10, 10);
	}

	@ModifyVariable(method = "renderClouds", at = @At("STORE"), ordinal = 7)
	private double modifyZ(double original, PoseStack poseStack, Matrix4f projectionMatrix, float partialTick, double camX, double camY, double camZ) {
		return (camZ / 12.0D + 0.33000001311302185D) - ClientWindManager.getCloudZ(partialTick);
	}

	@Unique
	private final Quaternion windRot = new Quaternion(0F, 0F, 0F, 1F);

	@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V", ordinal = 0, shift = At.Shift.BEFORE))
	private void renderLevelBefore(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo info) {
		this.windRot.set(0.0f, 0.0f, 0.0f, 1.0f);
		this.windRot.mul(Vector3f.XP.rotationDegrees((float) -ClientWindManager.getWindX(partialTick)));
		this.windRot.mul(Vector3f.ZP.rotationDegrees((float) -ClientWindManager.getWindZ(partialTick)));
		PoseStack poseStack2 = RenderSystem.getModelViewStack();
		poseStack2.pushPose();
		poseStack2.mulPose(this.windRot);
	}

	@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V", ordinal = 0, shift = At.Shift.AFTER))
	private void renderLevelAfter(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo info) {
		RenderSystem.getModelViewStack().popPose();
	}

	@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V", ordinal = 1, shift = At.Shift.BEFORE))
	private void renderLevelBefore1(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo info) {
		this.windRot.set(0.0f, 0.0f, 0.0f, 1.0f);
		this.windRot.mul(Vector3f.XP.rotationDegrees((float) -ClientWindManager.getWindX(partialTick)));
		this.windRot.mul(Vector3f.ZP.rotationDegrees((float) -ClientWindManager.getWindZ(partialTick)));
		PoseStack poseStack2 = RenderSystem.getModelViewStack();
		poseStack2.pushPose();
		poseStack2.mulPose(this.windRot);
	}

	@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V", ordinal = 1, shift = At.Shift.AFTER))
	private void renderLevelAfter1(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo info) {
		RenderSystem.getModelViewStack().popPose();
	}

}
