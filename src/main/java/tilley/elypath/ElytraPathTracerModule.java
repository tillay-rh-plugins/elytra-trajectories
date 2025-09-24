package tilley.elypath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.render.IRenderer3D;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.*;
import org.rusherhack.core.utils.ColorUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ElytraPathTracerModule extends ToggleableModule {

	// All Hail The Java
	/* Todo: replace the janky setVisibility code with proper way */
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
	private final NumberSetting<Float> destinationLineWidth = new NumberSetting<>("LineWidth", 2.5f, 0.5f, 15.0f).incremental(0.1f);
	private final BooleanSetting destinationDynamicColor = new BooleanSetting("DynamicColor", false);
	private final NumberSetting<Float> tillImpactSeconds = new NumberSetting<>("BeforeImpact", 2.5f, 0.05f, 10.0f).incremental(0.1f);
	private final ColorSetting destinationColor = new ColorSetting("Color", new Color(0x3a915ff0, false)).setVisibility(() -> !destinationDynamicColor.getValue());

	public ElytraPathTracerModule() {
		super("ElytraTrajectories", "Render a trajectory to predict where player will be going with elytra", ModuleCategory.RENDER);

		trajectoryDepthTest.setDescription("Allow the trajectory rendering to be not visible behind blocks.");
		// Todo: add more descriptions for vaguely named settings

		trajectoryColor.addSubSettings(trajectoryColorMode, trajectoryStaticColor, trajectoryGradientCustomColors);
		renderTrajectory.addSubSettings(trajectoryLineWidth, trajectoryDepthTest, trajectoryColor);
		trajectoryGradientCustomColors.addSubSettings(trajectoryGradientStart, trajectoryGradientEnd);
		renderDestination.addSubSettings(destinationFill, destinationOutline, destinationDynamicColor, destinationColor);
		destinationFill.addSubSettings(destinationAlpha);
		destinationOutline.addSubSettings(destinationLineWidth);
		destinationDynamicColor.addSubSettings(tillImpactSeconds);
		renderingSettings.addSubSettings(renderTrajectory, renderDestination);
		this.registerSettings(renderingSettings);
	}

	private List<Vec3> getTravelPoints() {
		/* Todo: Read elytra rocket boosting source code and see if i can skid that in order to render properly after a rocket is launched */

		List<Vec3> points = new ArrayList<>();

		if (mc.player == null || mc.level == null || !mc.player.isFallFlying()) return points;

		Vec3 vel = mc.player.getDeltaMovement();
		Vec3 pos = mc.player.position();
		Vec3 lookAngle = mc.player.getLookAngle();
		float xRot = mc.player.getXRot();

		while (true) {
			vel = updateFallFlyingMovement(vel, lookAngle, xRot);
			pos = pos.add(vel);

			BlockPos blockPos = BlockPos.containing(pos);
			if (!mc.level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ())))
				break;

			points.add(pos);

			if (!mc.level.getBlockState(blockPos).getCollisionShape(mc.level, blockPos).isEmpty()) break;
		}

		return points;
	}

	// skidded asf
	// I may or may not have no idea how this works, I just copied mc source code
	// and changed various things to make it work here and fit in with other features
	// OG src at net.minecraft.world.entity.LivingEntity # updateFallFlyingMovement
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

	@Subscribe
	private void onRender3D(EventRender3D event) {
		if (mc.player == null || !mc.player.isFallFlying() || mc.level == null) return;

		IRenderer3D renderer = event.getRenderer();
		List<Vec3> points = getTravelPoints();

		if (points.size() < 2) return;

		renderer.begin(event.getMatrixStack());

		BlockPos blockPos = BlockPos.containing(points.getLast());

		// Code to choose the color to render the destination and then render it
		if (renderDestination.getValue() && !mc.level.getBlockState(blockPos).getCollisionShape(mc.level, blockPos).isEmpty()) {
			renderer.setLineWidth(destinationLineWidth.getValue());
			int color = destinationDynamicColor.getValue()
					? (points.size() > tillImpactSeconds.getValue() * RusherHackAPI.getServerState().getTPS()
					? getDistanceColor(DistanceColorType.FAR)
					: getDistanceColor(DistanceColorType.CLOSE))
					: destinationColor.getValueRGB();

			renderer.drawBox(blockPos, destinationFill.getValue(), destinationOutline.getValue(), ColorUtils.transparency(color, destinationAlpha.getValue()));
		}

		// Use a different render function based on setting
		if (renderTrajectory.getValue()) {
			renderer.setLineWidth(trajectoryLineWidth.getValue());
			renderer.setDepthTest(trajectoryDepthTest.getValue());

			switch (trajectoryColorMode.getValue()) {
				case STATIC -> renderTrajectoryStatic(renderer, points);
				case GRADIENT -> renderTrajectoryGradient(renderer, points);
				case RAINBOW -> renderTrajectoryRainbow(renderer, points);
				case SPEED -> renderTrajectorySpeed(renderer, points);
			}
		}

		renderer.end();
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
