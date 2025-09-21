package tilley.elypath;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
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

    public enum ColorMode {
        STATIC,
        GRADIENT,
        RAINBOW,
        SPEED
    }

    // All Hail The Java
    private final NumberSetting<Float> predictTicks = new NumberSetting<>("PredictionTicks", 2000f, 1f, 2000f).incremental(1f);

    private final NullSetting renderingSettings = new NullSetting("Rendering");
    private final NullSetting trajectorySettings = new NullSetting("Trajectory");
    private final BooleanSetting renderDestination = new BooleanSetting("Destination", true);

    private final EnumSetting<ColorMode> trajectoryColorMode = new EnumSetting<>("Mode", ColorMode.STATIC);
    private final NumberSetting<Float> trajectoryLineWidth = new NumberSetting<>("LineWidth", 2.5f, 0.5f, 15.0f).incremental(0.1f);
    private final BooleanSetting trajectoryDepthTest = new BooleanSetting("DepthTest", false);
    private final NullSetting trajectoryColor = new NullSetting("Color");
    private final ColorSetting trajectoryStaticColor = new ColorSetting("Value", new Color(0x915ff0)).setVisibility(() -> trajectoryColorMode.getValue() == ColorMode.STATIC);
    private final BooleanSetting trajectoryGradientCustomColors = new BooleanSetting("CustomColors", false).setVisibility(() -> trajectoryColorMode.getValue() == ColorMode.GRADIENT);
    private final ColorSetting trajectoryGradientStart = new ColorSetting("Start", new Color(0x004fff)).setVisibility(() -> trajectoryColorMode.getValue() == ColorMode.GRADIENT && trajectoryGradientCustomColors.getValue());
    private final ColorSetting trajectoryGradientEnd = new ColorSetting("End", new Color(0x00ffff)).setVisibility(() -> trajectoryColorMode.getValue() == ColorMode.GRADIENT && trajectoryGradientCustomColors.getValue());
    private final NumberSetting<Float> trajectoryRainbowSaturation = new NumberSetting<>("Saturation", 0.78f, 0.0f, 1.0f).incremental(0.01f).setVisibility(() -> trajectoryColorMode.getValue() == ColorMode.RAINBOW);
    private final NumberSetting<Float> trajectoryRainbowBrightness = new NumberSetting<>("Brightness", 1.0f, 0.0f, 1.0f).incremental(0.01f).setVisibility(() -> trajectoryColorMode.getValue() == ColorMode.RAINBOW);

    private final BooleanSetting destinationFill = new BooleanSetting("Fill", true);
    private final BooleanSetting destinationOutline = new BooleanSetting("Outline", true);

    private final NumberSetting<Float> destinationLineWidth = new NumberSetting<>("LineWidth", 2.5f, 0.5f, 15.0f).incremental(0.1f);
    private final BooleanSetting destinationDepthTest = new BooleanSetting("DepthTest", false);
    private final ColorSetting destinationColor = new ColorSetting("Color", new Color(0x3a915ff0, true));

    public ElytraPathTracerModule() {
        super("ElytraTrajectories", "Render a trajectory to predict where player will be going with elytra", ModuleCategory.RENDER);

        predictTicks.setDescription("Amount of ticks into the future to render trajectory");

        trajectoryColor.addSubSettings(trajectoryColorMode,trajectoryStaticColor,trajectoryGradientCustomColors,trajectoryGradientStart,trajectoryGradientEnd,trajectoryRainbowBrightness,trajectoryRainbowSaturation);
        trajectorySettings.addSubSettings(trajectoryLineWidth,trajectoryDepthTest,trajectoryColor);
        renderDestination.addSubSettings(destinationFill,destinationOutline,destinationLineWidth,destinationDepthTest,destinationColor);
        renderingSettings.addSubSettings(trajectorySettings,renderDestination);
        this.registerSettings(predictTicks,renderingSettings);

    }

    // Return all the points the user will be at up to limit ticks in the future
    private List<Vec3> getTravelPoints(int limit) {
        if (mc.player == null || !mc.player.isFallFlying()) return new ArrayList<>();

        List<Vec3> points = new ArrayList<>(limit);
        Vec3 pos = mc.player.position();
        Vec3 vel = mc.player.getDeltaMovement();
        Vec3 lookAngle = mc.player.getLookAngle();
        float xRot = mc.player.getXRot();

        for (int i = 0; i < limit; i++) {
            vel = updateFallFlyingMovement(vel, lookAngle, xRot);
            pos = pos.add(vel);
            points.add(pos);
        }
        return points;
    }

    // Take in a list of points that user will go through without accounting for blocks
    // This returns a new list that only has points leading up to collision with a block
    private List<Vec3> cutOffAfterCollision(List<Vec3> points) {
        List<Vec3> newPoints = new ArrayList<>();
        for (Vec3 point : points) {
            newPoints.add(point);
            BlockPos blockPos = BlockPos.containing(point);
            if (!mc.level.getBlockState(blockPos).getCollisionShape(mc.level, blockPos).isEmpty()) {
                break;
            }
        }
        return newPoints;
    }


    // skidded asf
    // I may or may not have no idea how this works, I just copied mc source code
    // and changed various things to make it work here and fit in with other features
    // OG src at net.minecraft.world.entity.LivingEntity # updateFallFlyingMovement
    private Vec3 updateFallFlyingMovement(Vec3 vec3, Vec3 lookAngle, float xRot) {
        float f = xRot * ((float)Math.PI / 180F);
        double d = Math.sqrt(lookAngle.x * lookAngle.x + lookAngle.z * lookAngle.z);
        double e = vec3.horizontalDistance();
        double h = Mth.square(Math.cos(f));

        vec3 = vec3.add(0.0, 0.08 * (-1.0 + h * 0.75), 0.0);

        if (vec3.y < 0.0 && d > 0.0) {
            double i = vec3.y * -0.1 * h;
            vec3 = vec3.add(lookAngle.x * i / d, i, lookAngle.z * i / d);
        }

        if (f < 0.0F && d > 0.0) {
            double i = e * (-Mth.sin(f)) * 0.04;
            vec3 = vec3.add(-lookAngle.x * i / d, i * 3.2, -lookAngle.z * i / d);
        }

        if (d > 0.0) {
            vec3 = vec3.add((lookAngle.x / d * e - vec3.x) * 0.1, 0.0, (lookAngle.z / d * e - vec3.z) * 0.1);
        }

        return vec3.multiply(0.99, 0.98, 0.99);
    }

    @Subscribe
    private void onRender3D(EventRender3D event) {
        if (mc.player == null || !mc.player.isFallFlying() || mc.level == null) return;

        IRenderer3D renderer = event.getRenderer();
        int limit = Math.round(predictTicks.getValue()); // Maximum number of segments to make to avoid lag
        List<Vec3> points = cutOffAfterCollision(getTravelPoints(limit)); // Get a list of point objects to generate segments between

        // ChatUtils.print(new DecimalFormat("0.000").format(points.size() / RusherHackAPI.getServerState().getTPS()) + " seconds until impact.");

        if (points.size() < 2) return; // Make sure to never try to generate when ur only one tick away from collision

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

        renderer.begin(event.getMatrixStack());

        // If there is a collision, render a highlighted block at that location if we allow rendering
        // destination block
        if (renderDestination.getValue() && points.size() != limit) {
            renderer.setLineWidth(destinationLineWidth.getValue());
            renderer.setDepthTest(destinationDepthTest.getValue());
            BlockPos blockPos = BlockPos.containing(points.getLast());
            renderer.drawBox(blockPos, destinationFill.getValue(), destinationOutline.getValue(),
                    destinationColor.getValueRGB());
        }

        renderer.end();
    }

    private void renderTrajectoryStatic(IRenderer3D renderer, List<Vec3> points) {
        // Iterate through all the points and draw line segments between them
        Vec3 prevPoint = null;
        for (Vec3 point : points) {
            if (prevPoint != null) {
                renderer.drawLine(prevPoint.x, prevPoint.y, prevPoint.z, point.x, point.y, point.z,
                        trajectoryStaticColor.getValueRGB());
            }
            prevPoint = point;
        }
    }

    private void renderTrajectoryGradient(IRenderer3D renderer, List<Vec3> points) {
        final double stepFactor = 3d / points.size(); // Amplify the gradient so that it does not look bad from first
        // prt
        double factor = 0;
        Vec3 prevPoint = null;
        for (Vec3 point : points) {
            if (prevPoint != null) { // koteyka what the heck is going on here please make it distance from player based not this gooberness
                int color = ColorUtils.interpolateColor(
                        trajectoryGradientCustomColors.getValue() ? trajectoryGradientStart.getValueRGB() : getDistanceColor(DistanceColorType.CLOSE),
                        trajectoryGradientCustomColors.getValue() ? trajectoryGradientEnd.getValueRGB() : getDistanceColor(DistanceColorType.FAR), factor);
                renderer.drawLine(prevPoint.x, prevPoint.y, prevPoint.z, point.x, point.y, point.z, color);
                factor += stepFactor;
                if (factor > 1) factor = 1;
            }
            prevPoint = point;
        }
    }

    private void renderTrajectoryRainbow(IRenderer3D renderer, List<Vec3> points) {
        int maxColors = points.size();
        Vec3 prevPoint = null;

        for (int i = 0; i < points.size(); i++) {
            Vec3 point = points.get(i);
            if (prevPoint != null) {
                renderer.drawLine(prevPoint.x, prevPoint.y, prevPoint.z, point.x, point.y, point.z,
                        getRainbow(i, maxColors));
            }
            prevPoint = point;
        }
    }

    private void renderTrajectorySpeed(IRenderer3D renderer, List<Vec3> points) {
        double min = 0, max = 80;
        Vec3 prevPoint = null;
        for (Vec3 point : points) {
            if (prevPoint != null) {
                double d = point.distanceTo(prevPoint) * RusherHackAPI.getServerState().getTPS();
                renderer.drawLine(prevPoint.x, prevPoint.y, prevPoint.z, point.x, point.y, point.z, ColorUtils.blendColors(new int[]{getDistanceColor(DistanceColorType.CLOSE), getDistanceColor(DistanceColorType.FAR), 0xff00ffff}, (float) ((d - min) / (max - min))));
            }
            prevPoint = point;
        }
    }


    /**
     * @param idx Index in the point list.
     * @param total Size of the point list.
     * @return The resulting rainbow color.
     */
    private int getRainbow(int idx, int total) {
        float hue = (float) idx / total;
        int rgb = Color.HSBtoRGB(hue ,trajectoryRainbowSaturation.getValue(), trajectoryRainbowBrightness.getValue());
		return ColorUtils.transparency(rgb, 255);
    }

	private enum DistanceColorType {
		CLOSE,
		FAR
	}

	/**
	 * @return Colors -> Distance Colors -> Close / Far
	 */
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