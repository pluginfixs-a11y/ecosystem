package ecosystem;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Фейерверки (PVO). Ракета, выпущенная арбалетом, запоминается при загрузке; дальше мы смотрим
 * только за ней — пока список не пуст, тик стоит несколько сравнений векторов.
 * <p>
 * «Наведение» — разворот, которого ванильная ракета сделать не может. Порог низкий (6°): PVO ищет
 * цель в конусе 30°, и при нормальном прицеливании ракета доворачивается всего на пару градусов.
 * «Попадание» — взрыв такой ракеты по живому.
 * <p>
 * Срок жизни записи — по тикам сервера, а не сущности: у выгруженной ракеты свои тики не идут,
 * и запись с сильной ссылкой на сущность и игрока осталась бы навсегда.
 */
final class Rockets {
	private Rockets() {
	}

	private record Track(FireworkRocketEntity rocket, ServerPlayer owner, Vec3 heading, boolean homing, int born) {
	}

	/** Сколько тиков держим запись: PVO сам гасит ракету за 120 тиков. */
	private static final int LIFETIME = 200;
	private static final double TURN_DEGREES = 6.0;

	private static final Map<Integer, Track> ACTIVE = new HashMap<>(8);
	private static final Set<Integer> HOMING = new HashSet<>(8);

	static void onLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof FireworkRocketEntity rocket)) {
			return;
		}
		if (!(rocket.getOwner() instanceof ServerPlayer owner) || !rocket.isShotAtAngle()) {
			return;
		}
		ItemStack stack = rocket.getItem();
		Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
		if (fireworks == null || fireworks.explosions().isEmpty()) {
			return;
		}
		Vec3 motion = rocket.getDeltaMovement();
		if (motion.lengthSqr() < 1.0E-6) {
			return;
		}
		ACTIVE.put(rocket.getId(), new Track(rocket, owner, motion.normalize(), false, level.getServer().getTickCount()));
		Awards.award(owner, Awards.ROCKET);
	}

	static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		List<Integer> gone = null;
		for (Map.Entry<Integer, Track> entry : ACTIVE.entrySet()) {
			Track track = entry.getValue();
			if (track.rocket.isRemoved() || now - track.born > LIFETIME) {
				if (gone == null) {
					gone = new ArrayList<>(2);
				}
				gone.add(entry.getKey());
				continue;
			}
			if (track.homing) {
				continue;
			}
			// Смотрим только первые две секунды: дальше скорость падает, и направление
			// меняется уже от инерции и гравитации, а не от наведения.
			if (now - track.born > 40) {
				continue;
			}
			Vec3 motion = track.rocket.getDeltaMovement();
			if (motion.lengthSqr() < 1.0E-6) {
				continue;
			}
			double cos = track.heading.dot(motion.normalize());
			double degrees = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
			if (degrees > TURN_DEGREES) {
				entry.setValue(new Track(track.rocket, track.owner, track.heading, true, track.born));
				HOMING.add(entry.getKey());
				Awards.award(track.owner, Awards.ROCKET_LOCK);
			}
		}
		if (gone != null) {
			for (int id : gone) {
				ACTIVE.remove(id);
				HOMING.remove(id);
			}
		}
	}

	/** Взрыв ракеты, которая уже разворачивалась, — это и есть самонаведение. */
	static void onDamage(LivingEntity victim, DamageSource source, float baseDamage, float damage, boolean blocked) {
		if (HOMING.isEmpty()) {
			return;
		}
		Entity direct = source.getDirectEntity();
		if (direct == null || !HOMING.remove(direct.getId())) {
			return;
		}
		if (source.getEntity() instanceof ServerPlayer owner) {
			Awards.award(owner, Awards.ROCKET_HIT);
		}
	}

	/** Ракета выгрузилась вместе с чанком — запись больше не нужна. */
	static void onUnload(Entity entity, ServerLevel level) {
		if (entity == null) {
			return;
		}
		ACTIVE.remove(entity.getId());
		HOMING.remove(entity.getId());
	}

	static void forget(ServerPlayer player) {
		List<Integer> gone = null;
		for (Map.Entry<Integer, Track> entry : ACTIVE.entrySet()) {
			if (entry.getValue().owner == player) {
				if (gone == null) {
					gone = new ArrayList<>(2);
				}
				gone.add(entry.getKey());
			}
		}
		if (gone != null) {
			for (int id : gone) {
				ACTIVE.remove(id);
				HOMING.remove(id);
			}
		}
	}

	static void clear() {
		ACTIVE.clear();
		HOMING.clear();
	}
}
