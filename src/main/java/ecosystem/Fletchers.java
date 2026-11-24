package ecosystem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import ecosystem.mixin.AbstractArrowAccessor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Стрелы со стола лучника (fletcher) и колчан. Своей стрелы мод завести не может — это ванильная
 * {@code minecraft:arrow} с составом в {@code custom_data.fletcher}, поэтому «своя стрела»
 * проверяется по этому ключу. Каждую секунду, по инвентарю игроков: 41 слот — копейки.
 * <p>
 * Две вещи читаются по тому же контракту: {@code custom_data.fletcher.break} (железный наконечник
 * ломает блок) и {@code custom_data.fletcher_quiver} (пустой мешок, превращённый в колчан).
 */
final class Fletchers {
	private Fletchers() {
	}

	static final String ROOT = "fletcher";
	static final String QUIVER_ROOT = "fletcher_quiver";
	private static final String KEY_QUIVER = "quiver";
	private static final String KEY_BREAK = "break";
	private static final String KEY_EXPLODE = "explode";
	/** Тиков без движения, после которых стрела считается воткнувшейся и снимается с учёта. */
	private static final int STILL_TICKS = 3;

	/**
	 * Ломающие стрелы в полёте: uuid → прошлая позиция и владелец. Нужны для «Взлома»: событие ломания
	 * блока приходит и на обычную кирку, поэтому мало найти стрелу рядом — она должна лететь
	 * (у воткнувшейся стрелы позиция не меняется, и ломание её блока «Взломом» не считается).
	 */
	private static final Map<UUID, Flight> FLYING = new HashMap<>(4);

	static void tick(MinecraftServer server) {
		track();
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Inventory inventory = player.getInventory();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (stack.is(Items.ARROW) && isFletcher(stack)) {
					Awards.award(player, Awards.FLETCHER_ARROW);
					break;
				}
			}
		}
	}

	static boolean isFletcher(ItemStack stack) {
		return root(stack, ROOT) != null;
	}

	/** Стрела с железным наконечником: ломает блок, в который воткнулась. */
	static boolean breaks(ItemStack stack) {
		CompoundTag tag = root(stack, ROOT);
		return tag != null && tag.getBooleanOr(KEY_BREAK, false);
	}

	/** Стрела с порохом: взрывается на первом попадании. */
	static boolean explodes(ItemStack stack) {
		CompoundTag tag = root(stack, ROOT);
		return tag != null && tag.getBooleanOr(KEY_EXPLODE, false);
	}

	/** Пустой мешок, превращённый столом лучника в колчан. */
	static boolean isQuiver(ItemStack stack) {
		CompoundTag tag = root(stack, QUIVER_ROOT);
		return stack.getItem() instanceof BundleItem && tag != null && tag.getBooleanOr(KEY_QUIVER, false);
	}

	static void onLoad(Entity entity) {
		if (!Ecosystem.FLETCHER_LOADED || !(entity instanceof AbstractArrow arrow)) {
			return;
		}
		if (arrow.getOwner() instanceof ServerPlayer owner && breaks(arrow.getPickupItemStackOrigin())) {
			FLYING.put(arrow.getUUID(), new Flight(arrow, owner.getUUID(), arrow.position()));
		}
	}

	static void onUnload(Entity entity) {
		FLYING.remove(entity.getUUID());
	}

	/**
	 * Ломание блока через {@code PlayerBlockBreakEvents.AFTER}: железная стрела сломала блок и
	 * уничтожилась. Стрелу ищем по полёту, а не просто по близости — иначе «Взлом» выдавался бы
	 * за кирку рядом с воткнувшейся стрелой.
	 */
	static void onBlockBreak(Level world, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (!Ecosystem.FLETCHER_LOADED || FLYING.isEmpty() || !(world instanceof ServerLevel level)
				|| !(player instanceof ServerPlayer owner)) {
			return;
		}
		AABB area = new AABB(pos).inflate(1.5);
		for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, area)) {
			Flight flight = FLYING.get(arrow.getUUID());
			if (flight == null || !flight.owner().equals(owner.getUUID())) {
				continue;
			}
			// Ломает только та стрела, что воткнулась в блок в этом тике: пролетающая мимо — не она.
			if (!((AbstractArrowAccessor) arrow).ecosystem$inGround()) {
				continue;
			}
			// Стрела, которая простояла в блоке весь тик, ломанием не считается.
			if (flight.position().distanceToSqr(arrow.position()) < 0.01) {
				continue;
			}
			if (breaks(arrow.getPickupItemStackOrigin())) {
				Awards.award(owner, Awards.FLETCHER_BREAK);
				return;
			}
		}
	}

	private static void track() {
		if (FLYING.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Flight>> it = FLYING.entrySet().iterator(); it.hasNext(); ) {
			Flight flight = it.next().getValue();
			// Воткнувшаяся стрела живёт в мире до подбора, держать её в карте незачем.
			if (flight.arrow().isRemoved() || flight.still() >= STILL_TICKS) {
				it.remove();
				continue;
			}
			flight.movedTo(flight.arrow().position());
		}
	}

	static void clear() {
		FLYING.clear();
	}

	/** Корень {@code custom_data.<key>}; {@code null}, если тега нет. */
	private static CompoundTag root(ItemStack stack, String key) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag tag = data.copyTag();
		return tag.contains(key) ? tag.getCompoundOrEmpty(key) : null;
	}

	/** Полёт ломающей стрелы: сама стрела, её владелец и позиция на конец прошлого тика. */
	private static final class Flight {
		private final AbstractArrow arrow;
		private final UUID owner;
		private Vec3 position;
		private int still;

		Flight(AbstractArrow arrow, UUID owner, Vec3 position) {
			this.arrow = arrow;
			this.owner = owner;
			this.position = position;
		}

		AbstractArrow arrow() {
			return arrow;
		}

		UUID owner() {
			return owner;
		}

		Vec3 position() {
			return position;
		}

		int still() {
			return still;
		}

		void movedTo(Vec3 now) {
			still = now.distanceToSqr(position) < 0.0001 ? still + 1 : 0;
			this.position = now;
		}
	}
}
