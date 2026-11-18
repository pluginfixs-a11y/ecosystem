package ecosystem;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Вагонетки (fastminecarts). Скорость считается по вектору движения, который уже посчитала игра.
 * <p>
 * Пороги — не мгновенная скорость, а «продержись столько-то»: мод выставляет скорость сразу,
 * и мгновенные пороги выдали бы три достижения в один тик. Счётчики скорости обновляются раз
 * в 5 тиков, а отброс мобов проверяется каждый тик — мод толкает моба один раз, и в окно 5 тиков
 * он уже успевает улететь из коробки.
 */
final class Carts {
	private Carts() {
	}

	/** Блоков в секунду и сколько тиков на них надо продержаться. */
	private static final double SPEED = 20.0;
	private static final int SPEED_TICKS = 40;
	private static final double FAST = 35.0;
	private static final int FAST_TICKS = 100;
	private static final double MAX = 38.0;
	private static final int MAX_TICKS = 200;

	/** Скорость, при которой вагонетка реально сбивает мобов. */
	private static final double RUNOVER = 15.0;
	private static final int SAMPLE = 5;

	private static final class Held {
		int speed;
		int fast;
		int max;
	}

	private static final Map<UUID, Held> HELD = new HashMap<>(8);

	static void tick(MinecraftServer server) {
		boolean sample = server.getTickCount() % SAMPLE == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Entity vehicle = player.getVehicle();
			if (!(vehicle instanceof AbstractMinecart cart) || !cart.isOnRails()) {
				if (sample) {
					HELD.remove(player.getUUID());
				}
				continue;
			}
			double bps = cart.getDeltaMovement().length() * 20.0;
			if (bps >= RUNOVER && knockedSomeone(cart, player)) {
				Awards.award(player, Awards.CART_RUNOVER);
			}
			if (!sample) {
				continue;
			}
			Held held = HELD.computeIfAbsent(player.getUUID(), id -> new Held());
			held.speed = bps >= SPEED ? held.speed + SAMPLE : 0;
			held.fast = bps >= FAST ? held.fast + SAMPLE : 0;
			held.max = bps >= MAX ? held.max + SAMPLE : 0;

			if (held.speed >= SPEED_TICKS) {
				Awards.award(player, Awards.CART);
			}
			if (held.fast >= FAST_TICKS) {
				Awards.award(player, Awards.CART_FAST);
			}
			if (held.max >= MAX_TICKS) {
				Awards.award(player, Awards.CART_MAX);
			}
		}
	}

	/** Живой моб рядом с вагонеткой, которого она тащит по курсу, — это отброс. */
	private static boolean knockedSomeone(AbstractMinecart cart, ServerPlayer driver) {
		Vec3 motion = cart.getDeltaMovement();
		if (motion.lengthSqr() < 1.0E-4) {
			return false;
		}
		Vec3 heading = motion.normalize();
		return !cart.level().getEntities(cart, cart.getBoundingBox().inflate(3.0), entity -> {
			if (entity == driver || !(entity instanceof LivingEntity)) {
				return false;
			}
			Vec3 velocity = entity.getDeltaMovement();
			// Импульс fastminecarts — порядка 2.5 блока/тик; 0.2 хватает, чтобы отличить
			// отброс от шага моба, но не отсечь слабый толчок.
			if (velocity.lengthSqr() <= 0.04) {
				return false;
			}
			// Моб должен улетать по курсу и стоять не позади вагонетки: иначе хватало
			// любого дёрнувшегося моба рядом (курица, чужой удар, соседний поезд).
			if (velocity.normalize().dot(heading) <= 0.35) {
				return false;
			}
			return entity.position().subtract(cart.position()).dot(heading) > -1.5;
		}).isEmpty();
	}

	static void forget(UUID player) {
		HELD.remove(player);
	}

	static void clear() {
		HELD.clear();
	}
}
