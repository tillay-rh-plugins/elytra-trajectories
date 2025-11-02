package tilley.elypath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.Vec3;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.events.network.EventPacket;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.render.IRenderer3D;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.client.api.utils.InventoryUtils;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.*;
import org.rusherhack.core.utils.ColorUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class ElytraPathTracerModule extends ToggleableModule {

	// All Hail The Java
	@SuppressWarnings("FieldCanBeLocal")
	private final BooleanSetting predictRockets = new BooleanSetting("PredictRockets", true);
	private final NumberSetting<Float> offsetTicks = new NumberSetting<>("OffsetTicks", 11f, 0f, 22.0f).incremental(1f);
	private final NullSetting renderingSettings = new NullSetting("Rendering");
	private final BooleanSetting renderTrajectory = new BooleanSetting("Trajectory", true);
	private final BooleanSetting renderDestination = new BooleanSetting("Destination", true);
	private final EnumSetting<ColorMode> trajectoryColorMode = new EnumSetting<>("Mode", ColorMode.STATIC);
	private final NumberSetting<Float> trajectoryLineWidth = new NumberSetting<>("LineWidth", 2.5f, 0.5f, 15.0f).incremental(0.1f);
	private final BooleanSetting trajectoryDepthTest = new BooleanSetting("DepthTest", false);
	private final NullSetting trajectoryColor = new NullSetting("ColorSettings");
	private final ColorSetting trajectoryStaticColor = new ColorSetting("Value", new Color(0x915ff0)).setVisibility(() -> trajectoryColorMode.getValue() == ColorMode.STATIC);
	private final BooleanSetting trajectoryGradientCustomColors = new BooleanSetting("CustomColors", false).setVisibility(() -> trajectoryColorMode.getValue() == ColorMode.GRADIENT);
	private final ColorSetting trajectoryGradientStart = new ColorSetting("Start", new Color(0x004fff));
	private final ColorSetting trajectoryGradientEnd = new ColorSetting("End", new Color(0x00ffff));
	private final BooleanSetting destinationFill = new BooleanSetting("Fill", true);
	private final NumberSetting<Integer> destinationAlpha = new NumberSetting<>("Opacity", 150, 0, 255).incremental(1);
	private final BooleanSetting destinationOutline = new BooleanSetting("Outline", true);
	private final BooleanSetting destinationDynamicColor = new BooleanSetting("DynamicColor", false);
	private final NumberSetting<Float> tillImpactSeconds = new NumberSetting<>("BeforeImpact", 2.5f, 0.05f, 10.0f).incremental(0.1f);
	private final ColorSetting destinationColor = new ColorSetting("Color", new Color(0x3a915ff0, false)).setVisibility(() -> !destinationDynamicColor.getValue());

	// Global counter on how long is left on the current rocket boost
	private int rocketBoostTicks = 0;

	public ElytraPathTracerModule() {
		super("ElytraTrajectories", "Render a trajectory to predict where player will be going with elytra", ModuleCategory.RENDER);

		trajectoryDepthTest.setDescription("Allow the trajectory rendering to be not visible behind blocks");
		predictRockets.setDescription("Tries to predict new trajectory after rocket has been used");
		offsetTicks.setDescription("Ticks to make up for server side randomness to how long rocket boost lasts");
		destinationDynamicColor.setDescription("Change the color of the destination box based on how long until impact");
		tillImpactSeconds.setDescription("How many seconds before impact to change the color of the destination box");
		trajectoryColorMode.setDescription("Options for different ways to color the trajectory");

		predictRockets.addSubSettings(offsetTicks);
		trajectoryColor.addSubSettings(trajectoryColorMode, trajectoryStaticColor, trajectoryGradientCustomColors);
		renderTrajectory.addSubSettings(trajectoryLineWidth, trajectoryDepthTest, trajectoryColor);
		trajectoryGradientCustomColors.addSubSettings(trajectoryGradientStart, trajectoryGradientEnd);
		renderDestination.addSubSettings(destinationFill, destinationOutline, destinationDynamicColor, destinationColor);
		destinationFill.addSubSettings(destinationAlpha);
		destinationDynamicColor.addSubSettings(tillImpactSeconds);
		renderingSettings.addSubSettings(renderTrajectory, renderDestination);

		this.registerSettings(predictRockets, renderingSettings);
	}

	private List<Vec3> getTravelPoints() {
		List<Vec3> points = new ArrayList<>();
		if (mc.player == null || mc.level == null || !mc.player.isFallFlying()) return points;

		Vec3 vel = mc.player.getDeltaMovement();
		Vec3 pos = mc.player.position();
		Vec3 look = mc.player.getLookAngle();
		float xRot = mc.player.getXRot();

		if (rocketBoostTicks > 0) {
			List<Vec3> boostPoints = getBoostTravelPoints(pos, vel, look, rocketBoostTicks);
			points.addAll(boostPoints);
			if (!points.isEmpty()) {
				pos = points.getLast();
				if (boostPoints.size() >= 2) {
					Vec3 last = boostPoints.getLast();
					Vec3 prev = boostPoints.get(boostPoints.size() - 2);
					vel = last.subtract(prev);
				}
			}
		}

		while (true) {
			vel = updateFallFlyingMovement(vel, look, xRot);
			pos = pos.add(vel);
			BlockPos blockPos = BlockPos.containing(pos);
			if (!mc.level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()))) break;
			points.add(pos);
			if (!mc.level.getBlockState(blockPos).getCollisionShape(mc.level, blockPos).isEmpty()) break;
		}
		return points;
	}

	@Subscribe
	private void onUpdate(EventUpdate event) {
		// Decrease the remaining ticks of active boost (if there is one) every tick
		if (rocketBoostTicks > 0) rocketBoostTicks--;
	}

	// Taken from minecraft src
	private static Vec3 updateFallFlyingMovement(Vec3 vec3, Vec3 lookAngle, float xRot) {
		if (mc.player == null) return null;

		float f = xRot * ((float) Math.PI / 180F);
		double d = Math.sqrt(lookAngle.x * lookAngle.x + lookAngle.z * lookAngle.z);
		double e = vec3.horizontalDistance();
		boolean isGoingUp = mc.player.getDeltaMovement().y <= (double) 0.0F;
		double g = isGoingUp && mc.player.hasEffect(MobEffects.SLOW_FALLING) ? Math.min(mc.player.getGravity(), 0.01) : mc.player.getGravity();
		double h = Mth.square(Math.cos(f));
		vec3 = vec3.add(0.0F, g * ((double) -1.0F + h * (double) 0.75F), 0.0F);
		if (vec3.y < (double) 0.0F && d > (double) 0.0F) {
			double i = vec3.y * -0.1 * h;
			vec3 = vec3.add(lookAngle.x * i / d, i, lookAngle.z * i / d);
		}

		if (f < 0.0F && d > (double) 0.0F) {
			double i = e * (double) (-Mth.sin(f)) * 0.04;
			vec3 = vec3.add(-lookAngle.x * i / d, i * 3.2, -lookAngle.z * i / d);
		}

		if (d > (double) 0.0F) {
			vec3 = vec3.add((lookAngle.x / d * e - vec3.x) * 0.1, 0.0F, (lookAngle.z / d * e - vec3.z) * 0.1);
		}

		return vec3.multiply(0.99F, 0.98F, 0.99F);
	}

	// Do stuff whenever a rocket is used
	@Subscribe
	private void onPacketSend(EventPacket.Send event) {
		// Don't do anything if this isn't the exact type of packet we want
		if (!(event.getPacket() instanceof ServerboundUseItemPacket packet)) return;
		if (!predictRockets.getValue() || mc.player == null) return;
		if (packet.getHand() != InteractionHand.MAIN_HAND) return;

		// If a firework is used in this packet, set flight var to be duration of used firework (1,2,3)
		ItemStack stack = mc.player.getInventory().getItem(mc.player.getInventory().selected);
		if (!stack.is(Items.FIREWORK_ROCKET)) return;
		Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
		if (fireworks == null) return;
		int flight = fireworks.flightDuration();

		// Make classwide var ticks how long boost will be based on the rocket used (approximately)
		rocketBoostTicks = 10 * flight + Math.round(offsetTicks.getValue());
	}

	private List<Vec3> getBoostTravelPoints(Vec3 pos, Vec3 vel, Vec3 look, int ticks) {
		List<Vec3> points = new ArrayList<>();
		if (mc.level == null) return points; // Avoid super weird obscure crashes
		for (int i = 0; i < ticks; i++) {
			vel = applyRocketBoostStep(vel, look);
			pos = pos.add(vel);
			points.add(pos);
			BlockPos blockPos = BlockPos.containing(pos);
			if (!mc.level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()))
				|| !mc.level.getBlockState(blockPos).getCollisionShape(mc.level, blockPos).isEmpty()) break;
		}
		return points;
	}

	private static Vec3 applyRocketBoostStep(Vec3 vel, Vec3 look) {
		return vel.add(
				look.x * 0.1 + (look.x * 1.5 - vel.x) * 0.5,
				look.y * 0.1 + (look.y * 1.5 - vel.y) * 0.5,
				look.z * 0.1 + (look.z * 1.5 - vel.z) * 0.5
		);
	}

	@Subscribe
	private void onRender3D(EventRender3D event) {
		if (mc.player == null || !mc.player.isFallFlying() || mc.level == null) return;

		IRenderer3D renderer = event.getRenderer();
		List<Vec3> points = getTravelPoints();

		if (points.size() < 2) return;

		BlockPos blockPos = BlockPos.containing(points.getLast());

		// Code to choose the color to render the destination and then render it
		if (renderDestination.getValue() && !mc.level.getBlockState(blockPos).getCollisionShape(mc.level, blockPos).isEmpty()) {
			renderer.begin(event.getMatrixStack());

			renderer.setLineWidth(2f);
			int color = destinationDynamicColor.getValue()
					? (points.size() > tillImpactSeconds.getValue() * RusherHackAPI.getServerState().getTPS()
					? getDistanceColor(DistanceColorType.FAR)
					: getDistanceColor(DistanceColorType.CLOSE))
					: destinationColor.getValueRGB();

			renderer.drawBox(blockPos, destinationFill.getValue(), destinationOutline.getValue(), ColorUtils.transparency(color, destinationAlpha.getValue()));

			renderer.end();
		}

		// Use a different render function based on setting
		if (renderTrajectory.getValue()) {
			renderer.begin(event.getMatrixStack());

			renderer.setLineWidth(trajectoryLineWidth.getValue());
			renderer.setDepthTest(trajectoryDepthTest.getValue());

			switch (trajectoryColorMode.getValue()) {
				case STATIC -> renderTrajectoryStatic(renderer, points);
				case GRADIENT -> renderTrajectoryGradient(renderer, points);
				case RAINBOW -> renderTrajectoryRainbow(renderer, points);
				case SPEED -> renderTrajectorySpeed(renderer, points);
			}

			renderer.end();
		}
	}

	/* Trajectory Renderers */

	private void renderTrajectoryStatic(IRenderer3D renderer, List<Vec3> points) {
		for (int i = points.size() - 1; i > 0; i--) {
			Vec3 prevPoint = points.get(i - 1);
			Vec3 point = points.get(i);

			renderer.drawLine(prevPoint.x, prevPoint.y, prevPoint.z, point.x, point.y, point.z, trajectoryStaticColor.getValueRGB());
		}
	}

	private void renderTrajectoryGradient(IRenderer3D renderer, List<Vec3> points) {
		int closeColor = trajectoryGradientCustomColors.getValue() ? trajectoryGradientStart.getValueRGB() : getDistanceColor(DistanceColorType.CLOSE);
		int farColor = trajectoryGradientCustomColors.getValue() ? trajectoryGradientEnd.getValueRGB() : getDistanceColor(DistanceColorType.FAR);

		for (int i = points.size() - 1; i > 0; i--) {
			Vec3 prevPoint = points.get(i - 1);
			Vec3 point = points.get(i);

			int color;
			if (i <= 5) {
				color = closeColor;
			} else if (i <= 20) {
				double factor = (double) (i - 5) / (20 - 5);
				color = ColorUtils.interpolateColor(closeColor, farColor, factor);
			} else {
				color = farColor;
			}

			renderer.drawLine(prevPoint.x, prevPoint.y, prevPoint.z, point.x, point.y, point.z, color);
		}
	}

	private void renderTrajectoryRainbow(IRenderer3D renderer, List<Vec3> points) {
		for (int i = points.size() - 1; i > 0; i--) {
			Vec3 prevPoint = points.get(i - 1);
			Vec3 point = points.get(i);

			renderer.drawLine(prevPoint.x, prevPoint.y, prevPoint.z,
					point.x, point.y, point.z,
					getRainbow(i, points.size()));
		}
	}

	private void renderTrajectorySpeed(IRenderer3D renderer, List<Vec3> points) {
		double min = 0, max = 80;
		for (int i = points.size() - 1; i > 0; i--) {
			Vec3 prevPoint = points.get(i - 1);
			Vec3 point = points.get(i);

			double distance = point.distanceTo(prevPoint) * RusherHackAPI.getServerState().getTPS();
			float t = (float) ((distance - min) / (max - min));

			int blendedColor = ColorUtils.blendColors(new int[]{
					getDistanceColor(DistanceColorType.CLOSE),
					getDistanceColor(DistanceColorType.FAR),
					Color.cyan.getRGB()
			}, t);

			renderer.drawLine(prevPoint.x, prevPoint.y, prevPoint.z, point.x, point.y, point.z, blendedColor);
		}
	}

	private int getRainbow(int idx, int total) {
		float hue = (float) idx / total;
		int rgb = Color.HSBtoRGB(hue, 0.8F,1);
		return ColorUtils.transparency(rgb, 255);
	}

	public enum ColorMode {
		STATIC,
		GRADIENT,
		RAINBOW,
		SPEED
	}

	private enum DistanceColorType {
		CLOSE,
		FAR
	}

	private static int getDistanceColor(DistanceColorType type) {
		int defaultColor = type == DistanceColorType.CLOSE ? Color.RED.getRGB() : Color.GREEN.getRGB();

		var distanceColors = getDistanceColorsSetting();
		if (distanceColors == null) return defaultColor;

		var colorSetting = distanceColors.getSubSetting(type == DistanceColorType.CLOSE ? "Close" : "Far");
		if (!(colorSetting instanceof ColorSetting cs)) return defaultColor;

		return cs.getValueRGB();
	}

	private static Setting<?> getDistanceColorsSetting() {
		return RusherHackAPI.getModuleManager()
				.getFeature("Colors")
				.map(module -> module.getSetting("Distance Colors"))
				.orElse(null);
	}

}
