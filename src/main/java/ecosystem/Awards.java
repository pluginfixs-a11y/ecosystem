package ecosystem;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Выдача достижений и «капитанское» достижение.
 * <p>
 * Критерии, которые нельзя выразить ванильным триггером, объявлены как {@code minecraft:impossible}
 * и выдаются отсюда: {@link net.minecraft.server.PlayerAdvancements#award} ставит критерий ровно так же,
 * как это сделал бы триггер, — тосты, чат и дерево работают штатно.
 */
public final class Awards {
	// Первая нода каждой ветки: из них собирается «Свояк».
	public static final String FIELD = "field";
	public static final String CAULDRON = "cauldron";
	public static final String LADDER = "ladder";
	public static final String CART = "cart";
	public static final String ROCKET = "rocket";
	public static final String WARP_ANCHOR = "warp_anchor";
	public static final String ENCHANT = "enchant";
	public static final String STEED_RIDE = "steed_ride";
	public static final String MOB_HIKE = "mob_hike";
	public static final String GHOST_HIDE = "ghost_hide";
	public static final String FLETCHER_TABLE = "fletcher_table";

	// Остальные ноды.
	public static final String FIELD_TILL = "field_till";
	public static final String FIELD_SOW = "field_sow";
	public static final String CAULDRON_ALL = "cauldron_all";
	public static final String LADDER_LONG = "ladder_long";
	public static final String CART_FAST = "cart_fast";
	public static final String CART_MAX = "cart_max";
	public static final String CART_RUNOVER = "cart_runover";
	public static final String ROCKET_LOCK = "rocket_lock";
	public static final String ROCKET_HIT = "rocket_hit";
	public static final String WARP_PAIR = "warp_pair";
	public static final String WARP_RIDE = "warp_ride";
	public static final String WARP_AGAIN = "warp_again";
	public static final String ENCHANT_TREASURE = "enchant_treasure";
	public static final String STEED_ARMOR = "steed_armor";
	public static final String STEED_HORN = "steed_horn";
	public static final String STEED_RECALL = "steed_recall";
	public static final String MOB_DEEP = "mob_deep";
	public static final String MOB_SCALED = "mob_scaled";
	public static final String MOB_ARROW = "mob_arrow";
	public static final String GHOST_SHOW = "ghost_show";
	public static final String GHOST_SIGN = "ghost_sign";
	public static final String GHOST_TEXT_BACK = "ghost_text_back";
	public static final String FLETCHER_ARROW = "fletcher_arrow";
	public static final String FLETCHER_KILL = "fletcher_kill";
	public static final String ALL = "all";

	static final String[] BRANCHES = {
			FIELD, CAULDRON, LADDER, CART, ROCKET, WARP_ANCHOR, ENCHANT, STEED_RIDE, MOB_HIKE, GHOST_HIDE,
			FLETCHER_TABLE
	};

	/** Полный список файлов {@code data/ecosystem/advancement/*.json} — для самопроверки на старте. */
	static final String[] IDS = {
			"root",
			FIELD, FIELD_TILL, FIELD_SOW,
			CAULDRON, CAULDRON_ALL,
			LADDER, LADDER_LONG,
			CART, CART_FAST, CART_MAX, CART_RUNOVER,
			ROCKET, ROCKET_LOCK, ROCKET_HIT,
			WARP_ANCHOR, WARP_PAIR, WARP_RIDE, WARP_AGAIN,
			ENCHANT, ENCHANT_TREASURE,
			STEED_RIDE, STEED_ARMOR, STEED_HORN, STEED_RECALL,
			MOB_HIKE, MOB_DEEP, MOB_SCALED, MOB_ARROW,
			GHOST_HIDE, GHOST_SHOW, GHOST_SIGN, GHOST_TEXT_BACK,
			FLETCHER_TABLE, FLETCHER_ARROW, FLETCHER_KILL,
			ALL
	};

	/** Ключ критерия во всех достижениях мода. */
	static final String CRITERION = "code";

	private Awards() {
	}

	public static boolean award(ServerPlayer player, String path) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return false;
		}
		AdvancementHolder holder = holder(server, path);
		if (holder == null) {
			return false;
		}
		// «Свояк» проверяется не здесь, а в миксине на PlayerAdvancements#award: через него проходит
		// и наша выдача, и ванильные триггеры, поэтому ветка на `enchanted_item`/`started_riding`
		// не оставляет капстоун висеть до следующей выдачи кода.
		if (!player.getAdvancements().award(holder, CRITERION)) {
			return false;
		}
		if (Ecosystem.DEBUG) {
			Ecosystem.LOGGER.info("Ecosystem: {} -> {}", player.getPlainTextName(), path);
		}
		return true;
	}

	private static AdvancementHolder holder(MinecraftServer server, String path) {
		return server.getAdvancements().get(Identifier.fromNamespaceAndPath(Ecosystem.MOD_ID, path));
	}

	/** Любая выдача ноды мода: если это голова ветки, проверить «Свояка». Зовётся из миксина. */
	public static void onAwarded(ServerPlayer player, AdvancementHolder holder) {
		if (player == null || holder == null) {
			return;
		}
		Identifier id = holder.id();
		// Сам «Свояк» проверку не запускает: иначе выдача капстоуна рекурсивно звала бы себя же.
		if (!Ecosystem.MOD_ID.equals(id.getNamespace()) || ALL.equals(id.getPath())) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server != null) {
			checkCapstone(player, server);
		}
	}

	/** «Свояк»: по одному трюку каждого мода. */
	static void checkCapstone(ServerPlayer player, MinecraftServer server) {
		if (isDone(player, holder(server, ALL))) {
			return;
		}
		for (String branch : BRANCHES) {
			// Без мода стол лучника не существует: его ветку не ждём, иначе «Свояк» недостижим.
			if (FLETCHER_TABLE.equals(branch) && !Ecosystem.FLETCHER_LOADED) {
				continue;
			}
			AdvancementHolder holder = holder(server, branch);
			if (holder == null) {
				return;
			}
			AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
			if (!progress.isDone()) {
				return;
			}
		}
		AdvancementHolder all = holder(server, ALL);
		if (all != null && player.getAdvancements().award(all, CRITERION) && Ecosystem.DEBUG) {
			Ecosystem.LOGGER.info("Ecosystem: {} -> {}", player.getPlainTextName(), ALL);
		}
	}

	private static boolean isDone(ServerPlayer player, AdvancementHolder holder) {
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}
}
