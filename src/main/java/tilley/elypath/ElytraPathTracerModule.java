package tilley.elypath;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.render.IRenderer3D;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;

public class ElytraPathTracerModule extends ToggleableModule {

    final NumberSetting<Float> predictTicks = new NumberSetting<>("PredictTicks", 2000f, 1f, 10000f).incremental(1f);

    public ElytraPathTracerModule() {
        super("ElytraTrajectories", "Render a trajectory to predict where player will be going with elytra", ModuleCategory.RENDER);

        predictTicks.setDescription("Amount of ticks into the future to render trajectory");

        this.registerSettings(predictTicks);
    }

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

    // skidded asf
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
        if (mc.player == null || !mc.player.isFallFlying()) return;

        IRenderer3D renderer = event.getRenderer();
        renderer.setLineWidth(12.0F);
        List<Vec3> points = getTravelPoints(Math.round(predictTicks.getValue()));
        if (points.size() < 2) return;

        renderer.begin(event.getMatrixStack());

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 p1 = points.get(i);
            Vec3 p2 = points.get(i + 1);
            renderer.drawLine(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, 0xffff0000);
        }

        renderer.end();

    }
}