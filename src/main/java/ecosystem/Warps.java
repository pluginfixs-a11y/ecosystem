package ecosystem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Лодстоуны (lodestonewarps). Раз в 10 тиков смотрим на компас в руке и на прыжки игрока:
 * варп всегда заканчивается у лодстоуна, поэтому «телепорт» проверяется наличием лодстоуна рядом.
 */
final class Warps {
	private Warps() {
	}

	private static final int PERIOD = 10;
	/** За 10 тиков даже на элитрах не пролететь столько. */
	private static final double JUMP = 64.0;
	private static final int LODESTONE_RADIUS = 4;
	/** Второй варп за это время — «туда и обратно»; ближе 10 с — это один и тот же прыжок. */
	private static final long AGAIN_MIN_MS = 10_000L;
	private static final long AGAIN_MAX_MS = 600_000L;

	private static final class Watch {
		Vec3 last;
		ResourceKey<Level> dimension;
		boolean glint;
		/** Сколько компасов восстановления было в инвентаре, когда компас заблестел. */
		int compasses;
		long lastWarp;
	}

	private static final Map<UUID, Watch> WATCH = new HashMap<>(8);

	static void tick(MinecraftServer server) {
		if (server.getTickCount() % PERIOD != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Watch watch = WATCH.computeIfAbsent(player.getUUID(), id -> new Watch());
			boolean glint = glint(player);
			if (glint && !watch.glint) {
				// Компас заблестел — первый лодстоун привязан.
				watch.compasses = countCompasses(player);
				Awards.award(player, Awards.WARP_ANCHOR);
			} else if (watch.glint && !glint) {
				// Блеск снят не сам по себе: варп израсходовал компас (в креативе — нет).
				// Смена слота или выброшенный компас тоже убирают блеск, но не меняют счёт в креативе
				// и не проходят проверку «стало меньше» — так «Пара» не выдаётся за смену хотбара.
				boolean consumed = !player.hasInfiniteMaterials() && countCompasses(player) < watch.compasses;
				if (consumed && nearLodestone(player)) {
					Awards.award(player, Awards.WARP_PAIR);
				}
			}
			watch.glint = glint;

			Vec3 now = player.position();
			ResourceKey<Level> dimension = player.level().dimension();
			if (watch.last != null) {
				double moved = now.distanceTo(watch.last);
				boolean otherDimension = watch.dimension != null && !watch.dimension.equals(dimension);
				if ((moved > JUMP || otherDimension) && nearLodestone(player)) {
					Awards.award(player, Awards.WARP_RIDE);
					long time = System.currentTimeMillis();
					long since = time - watch.lastWarp;
					if (watch.lastWarp > 0 && since >= AGAIN_MIN_MS && since <= AGAIN_MAX_MS) {
						Awards.award(player, Awards.WARP_AGAIN);
					}
					watch.lastWarp = time;
				}
			}
			watch.last = now;
			watch.dimension = dimension;
		}
	}

	private static boolean glint(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		return stack.is(Items.RECOVERY_COMPASS)
				&& Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
	}

	private static int countCompasses(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		int total = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(Items.RECOVERY_COMPASS)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static boolean nearLodestone(ServerPlayer player) {
		BlockPos center = player.blockPosition();
		if (!player.level().isLoaded(center)) {
			return false;
		}
		for (int dx = -LODESTONE_RADIUS; dx <= LODESTONE_RADIUS; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -LODESTONE_RADIUS; dz <= LODESTONE_RADIUS; dz++) {
					BlockPos sample = center.offset(dx, dy, dz);
					// Без isLoaded клик у края прогрузки тянул бы синхронную загрузку чанков.
					if (player.level().isLoaded(sample)
							&& player.level().getBlockState(sample).is(Blocks.LODESTONE)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	static void forget(UUID player) {
		WATCH.remove(player);
	}

	/** После смерти позиция «прыгает» на точку спавна — это не варп. */
	static void onRespawn(ServerPlayer player) {
		WATCH.remove(player.getUUID());
	}

	static void clear() {
		WATCH.clear();
	}
}
