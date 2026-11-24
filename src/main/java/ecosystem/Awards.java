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
	public static final String FISH = "fish";

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
	public static final String MOB_EDGE = "mob_edge";
	public static final String MOB_SCALED = "mob_scaled";
	public static final String MOB_ARROW = "mob_arrow";
	public static final String GHOST_SHOW = "ghost_show";
	public static final String GHOST_SIGN = "ghost_sign";
	public static final String GHOST_TEXT_BACK = "ghost_text_back";
	public static final String FLETCHER_ARROW = "fletcher_arrow";
	public static final String FLETCHER_QUIVER = "fletcher_quiver";
	public static final String FLETCHER_BREAK = "fletcher_break";
	public static final String FLETCHER_KILL = "fletcher_kill";
	public static final String FLETCHER_BLAST = "fletcher_blast";
	public static final String FISH_PERFECT = "fish_perfect";
	public static final String FISH_DEEP = "fish_deep";
	public static final String FISH_LIVE = "fish_live";
	public static final String FISH_TROPHY = "fish_trophy";
	public static final String FISH_STORM = "fish_storm";
	public static final String FISH_BEAST = "fish_beast";
	public static final String ALL = "all";

	/**
	 * Чужая вкладка, которую «Свояк» тоже ждёт: {@code anomaly} держит своё дерево и свою выдачу,
	 * поэтому ecosystem не дублирует его ноды, а читает готовое достижение из общего реестра.
	 * Связь — по id данных, а не по классам: сборка от anomaly не зависит.
	 */
	static final String ANOMALY_NS = "anomaly";
	static final String ANOMALY_PATH = "survived";

	static final String[] BRANCHES = {
			FIELD, CAULDRON, LADDER, CART, ROCKET, WARP_ANCHOR, ENCHANT, STEED_RIDE, MOB_HIKE, GHOST_HIDE,
			FLETCHER_TABLE, FISH
	};

	/**
	 * Ветка → мод, который её выдаёт. Без мода ветку не ждём: иначе «Свояк» остался бы недостижимым навсегда,
	 * а игрок ждал бы достижение за механику, которой на сервере нет. Порядок обязан совпадать с {@link #BRANCHES}.
	 */
	static final String[][] BRANCH_MODS = {
			{FIELD, "hoefarm"},
			{CAULDRON, "cauldronwash"},
			{LADDER, "ladderhang"},
			{CART, "fastminecarts"},
			{ROCKET, "pvo"},
			{WARP_ANCHOR, "lodestonewarps"},
			{ENCHANT, "enchantxp"},
			{STEED_RIDE, "steed"},
			{MOB_HIKE, "mobscaler"},
			{GHOST_HIDE, "ghostframe"},
			{FLETCHER_TABLE, "fletcher"},
			{FISH, "brine"}
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
			MOB_HIKE, MOB_DEEP, MOB_EDGE, MOB_SCALED, MOB_ARROW,
			GHOST_HIDE, GHOST_SHOW, GHOST_SIGN, GHOST_TEXT_BACK,
			FLETCHER_TABLE, FLETCHER_ARROW, FLETCHER_QUIVER, FLETCHER_BREAK, FLETCHER_KILL, FLETCHER_BLAST,
			FISH, FISH_PERFECT, FISH_DEEP, FISH_LIVE, FISH_TROPHY, FISH_STORM, FISH_BEAST,
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
		AdvancementHolder holder = holder(server, Ecosystem.MOD_ID, path);
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

	/** Любая выдача ноды мода: если это голова ветки, проверить «Свояка». Зовётся из миксина. */
	public static void onAwarded(ServerPlayer player, AdvancementHolder holder) {
		if (player == null || holder == null) {
			return;
		}
		Identifier id = holder.id();
		boolean ours = Ecosystem.MOD_ID.equals(id.getNamespace());
		// Чужая вкладка важна только одна: последняя нода «Свояка» может прийти из дерева anomaly.
		if (!ours && (!ANOMALY_NS.equals(id.getNamespace()) || !ANOMALY_PATH.equals(id.getPath()))) {
			return;
		}
		// Сам «Свояк» проверку не запускает: иначе выдача капстоуна рекурсивно звала бы себя же.
		if (ours && ALL.equals(id.getPath())) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server != null) {
			checkCapstone(player, server);
		}
	}

	/** «Свояк»: по одному трюку каждого мода. */
	static void checkCapstone(ServerPlayer player, MinecraftServer server) {
		if (isDone(player, holder(server, Ecosystem.MOD_ID, ALL))) {
			return;
		}
		int required = 0;
		for (String[] pair : BRANCH_MODS) {
			// Без мода ветки не существует: её не ждём, иначе «Свояк» недостижим.
			if (!Ecosystem.loaded(pair[1])) {
				continue;
			}
			required++;
			AdvancementHolder holder = holder(server, Ecosystem.MOD_ID, pair[0]);
			if (holder == null) {
				return;
			}
			AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
			if (!progress.isDone()) {
				return;
			}
		}
		// Ни одного мода набора на сервере: «Свояк» не за что давать, это была бы пустая награда за вход.
		if (required == 0) {
			return;
		}
		// Дерево anomaly — тоже часть набора: его ноду читаем из реестра, а не дублируем у себя.
		if (Ecosystem.ANOMALY_LOADED) {
			AdvancementHolder anomaly = holder(server, ANOMALY_NS, ANOMALY_PATH);
			if (anomaly == null || !player.getAdvancements().getOrStartProgress(anomaly).isDone()) {
				return;
			}
		}
		AdvancementHolder all = holder(server, Ecosystem.MOD_ID, ALL);
		if (all != null && player.getAdvancements().award(all, CRITERION) && Ecosystem.DEBUG) {
			Ecosystem.LOGGER.info("Ecosystem: {} -> {}", player.getPlainTextName(), ALL);
		}
	}

	private static AdvancementHolder holder(MinecraftServer server, String namespace, String path) {
		return server.getAdvancements().get(Identifier.fromNamespaceAndPath(namespace, path));
	}

	private static boolean isDone(ServerPlayer player, AdvancementHolder holder) {
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}
}
