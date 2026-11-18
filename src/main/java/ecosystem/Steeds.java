package ecosystem;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Лошади (steed). Привязка рога видна прямо в предмете: steed пишет её в ванильный
 * {@code minecraft:custom_data} под ключом {@code steed}. Вызов подтверждаем не звуком, а тем,
 * что лошадь с этим UUID действительно оказалась рядом.
 */
final class Steeds {
	private Steeds() {
	}

	/** Вызов идёт 2 секунды и может ждать прогрузки чанков — даём 11 секунд. */
	private static final int RECALL_TICKS = 220;

	private static final class Recall {
		final ServerPlayer player;
		final UUID horse;
		int ticks;

		Recall(ServerPlayer player, UUID horse, int ticks) {
			this.player = player;
			this.horse = horse;
			this.ticks = ticks;
		}
	}

	private static final List<Recall> RECALLS = new ArrayList<>(2);

	static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hit) {
		if (level.isClientSide() || !(entity instanceof AbstractHorse horse)) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		// Присед у steed оставлен ванили: рог в приседе ничего не привязывает.
		if (player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!held.has(DataComponents.INSTRUMENT)) {
			return InteractionResult.PASS;
		}
		UUID bound = boundHorse(held);
		if (bound == null || !bound.equals(horse.getUUID())) {
			Awards.award(serverPlayer, Awards.STEED_HORN);
		}
		return InteractionResult.PASS;
	}

	static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!held.has(DataComponents.INSTRUMENT)) {
			return InteractionResult.PASS;
		}
		UUID bound = boundHorse(held);
		if (bound == null) {
			return InteractionResult.PASS;
		}
		// Лошадь и так рядом — доказывать нечего, иначе достижение выдалось бы просто за дуновение.
		Entity nearby = player.level().getEntity(bound);
		if (nearby != null && nearby.distanceToSqr(player) < 900.0) {
			return InteractionResult.PASS;
		}
		// Повторные дуновения по тому же рогу не плодят ожидания.
		RECALLS.removeIf(recall -> recall.player == serverPlayer && recall.horse.equals(bound));
		RECALLS.add(new Recall(serverPlayer, bound, RECALL_TICKS));
		return InteractionResult.PASS;
	}

	static void forget(ServerPlayer player) {
		RECALLS.removeIf(recall -> recall.player == player);
	}

	static void tick() {
		if (RECALLS.isEmpty()) {
			return;
		}
		Iterator<Recall> iterator = RECALLS.iterator();
		while (iterator.hasNext()) {
			Recall recall = iterator.next();
			if (recall.player.isRemoved() || --recall.ticks <= 0) {
				iterator.remove();
				continue;
			}
			Entity entity = recall.player.level().getEntity(recall.horse);
			if (entity instanceof AbstractHorse horse && horse.isAlive()
					&& horse.distanceToSqr(recall.player) < 100.0) {
				Awards.award(recall.player, Awards.STEED_RECALL);
				iterator.remove();
			}
		}
	}

	static void clear() {
		RECALLS.clear();
	}

	/** UUID привязанной лошади из {@code custom_data.steed.horse}; null — рог свободен. */
	private static UUID boundHorse(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag own = data.copyTag().getCompoundOrEmpty("steed");
		String value = own.getStringOr("horse", "");
		if (value.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
