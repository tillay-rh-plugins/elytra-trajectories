package tilley.elypath;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;

public class ElytrapathPlugin extends Plugin {
	@Override
	public void onLoad() {
		this.getLogger().info("loaded elytrapath-plugin");
		RusherHackAPI.getModuleManager().registerFeature(new ElytraPathTracerModule());
	}
	@Override
	public void onUnload() {
		this.getLogger().info("elytrapath-plugin unloaded!");
	}
}
