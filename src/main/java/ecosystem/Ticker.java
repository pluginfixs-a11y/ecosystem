package ecosystem;

import net.minecraft.server.MinecraftServer;

/** Один тик на весь мод: дешёвые проверки, тяжёлое — только по событию. */
final class Ticker {
	private Ticker() {
	}

	static void tick(MinecraftServer server) {
		BlockClicks.flush(server);
		Frames.flush(server);
		Carts.tick(server);
		Rockets.tick(server);
		Warps.tick(server);
		Steeds.tick();
		Mobs.tick(server);
		Fletchers.tick(server);
		Fishing.tick(server);
	}

	static void clear() {
		BlockClicks.clear();
		Frames.clear();
		Carts.clear();
		Rockets.clear();
		Warps.clear();
		Steeds.clear();
	}
}
