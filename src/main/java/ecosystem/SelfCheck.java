package ecosystem;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Проверка на старте: все файлы достижений загрузились, у выдаваемых кодом критерий называется так,
 * как его ставит {@link Awards}, и ветки «Свояка» существуют.
 * <p>
 * Без этого опечатка в ключе критерия или разъехавшийся список веток проявились бы только тем, что
 * достижение (или весь капстоун) никогда не выдаётся — молча, без единой строки в логе.
 */
final class SelfCheck {
	/** Ноды на ванильных триггерах: их критерий ставит игра, а не код. */
	private static final Set<String> VANILLA_TRIGGERS = Set.of(
			"root", Awards.ENCHANT, Awards.ENCHANT_TREASURE, Awards.STEED_RIDE, Awards.STEED_ARMOR);

	private SelfCheck() {
	}

	static void run(MinecraftServer server) {
		Set<String> known = new HashSet<>(Awards.IDS.length * 2);
		Collections.addAll(known, Awards.IDS);

		int missing = 0;
		int badCriterion = 0;
		StringBuilder problems = null;

		for (String id : Awards.IDS) {
			AdvancementHolder holder = holder(server, id);
			if (holder == null) {
				missing++;
				problems = append(problems, id + "(нет)");
				continue;
			}
			if (!VANILLA_TRIGGERS.contains(id) && !holder.value().criteria().containsKey(Awards.CRITERION)) {
				badCriterion++;
				problems = append(problems, id + "(критерий)");
			}
		}

		int badBranch = 0;
		for (String branch : Awards.BRANCHES) {
			if (!known.contains(branch) || holder(server, branch) == null) {
				badBranch++;
				problems = append(problems, branch + "(ветка)");
			}
		}

		if (problems == null) {
			Ecosystem.LOGGER.info("Ecosystem: вкладка цела — нод: {}, веток «Свояка»: {}",
					Awards.IDS.length, Awards.BRANCHES.length);
		} else {
			Ecosystem.LOGGER.warn("Ecosystem: не загружено {} из {}, критерий не тот у {}, битых веток {}:{}",
					missing, Awards.IDS.length, badCriterion, badBranch, problems);
		}
	}

	private static AdvancementHolder holder(MinecraftServer server, String id) {
		return server.getAdvancements().get(Identifier.fromNamespaceAndPath(Ecosystem.MOD_ID, id));
	}

	private static StringBuilder append(StringBuilder problems, String problem) {
		if (problems == null) {
			problems = new StringBuilder();
		}
		return problems.append(' ').append(problem);
	}
}
