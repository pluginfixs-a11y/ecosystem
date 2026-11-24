package ecosystem;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Рыбалка ({@code brine}): мод ставит на игрока ванильные теги сущности ({@code Entity#addTag}),
 * здесь они читаются и превращаются в ноды. Классов brine тут нет — только строки-имена, поэтому
 * ecosystem собирается и без него, а в рантайме всё закрыто {@link Ecosystem#BRINE_LOADED}.
 * <p>
 * Почему теги, а не своя статистика: кастомная статистика ({@code STATS.CUSTOM}) уходит клиенту
 * пакетом {@code award_stats} числовым id из реестра {@code minecraft:custom_stat}, а этот реестр
 * клиенту не синхронизируется — серверный мод, заведя свой счётчик, разорвал бы соединение игроку
 * на первом же улове (проверено по исходникам 26.2: {@code ByteBufCodecs.registry} + {@code IdMap#byIdOrThrow}).
 * Теги живут только на сервере, сохраняются в NBT игрока и переживают рестарт.
 * <p>
 * Имена — контракт с {@code brine.Marks}; сверяются скриптом {@code check-eco-data.ps1}.
 */
final class Fishing {
	/** Любой улов через борьбу. */
	static final String CAUGHT = "brine:caught";
	/** Бой без единого промаха. */
	static final String PERFECT = "brine:perfect";
	/** Улов в пещерной воде (группа cave). */
	static final String CAVE = "brine:cave";
	/** Пришло ведро с живой рыбой. */
	static final String LIVE = "brine:live";
	/** Живая рыба 1.6× и крупнее. */
	static final String TROPHY = "brine:trophy";
	/** Улов в грозу. */
	static final String STORM = "brine:storm";
	/** С крючка вылезла живая тварь — она и была уловом. */
	static final String BEAST = "brine:beast";

	private Fishing() {
	}

	static void tick(MinecraftServer server) {
		if (!Ecosystem.BRINE_LOADED || server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.entityTags().contains(CAUGHT)) {
				Awards.award(player, Awards.FISH);
			}
			if (player.entityTags().contains(PERFECT)) {
				Awards.award(player, Awards.FISH_PERFECT);
			}
			if (player.entityTags().contains(CAVE)) {
				Awards.award(player, Awards.FISH_DEEP);
			}
			if (player.entityTags().contains(LIVE)) {
				Awards.award(player, Awards.FISH_LIVE);
			}
			if (player.entityTags().contains(TROPHY)) {
				Awards.award(player, Awards.FISH_TROPHY);
			}
			if (player.entityTags().contains(STORM)) {
				Awards.award(player, Awards.FISH_STORM);
			}
			if (player.entityTags().contains(BEAST)) {
				Awards.award(player, Awards.FISH_BEAST);
			}
		}
	}
}
