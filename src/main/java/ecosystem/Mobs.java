package ecosystem;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;

/**
 * Дальние земли (mobscaler). Мод метит обработанных мобов тегом {@code mobscaler:scaled},
 * а скелетам с эффектными стрелами ставит {@code mobscaler:arrow:<эффект>} — по этим тегам
 * всё видно без единой правки в самом моде.
 * <p>
 * Достижения здесь про разные действия: дойти, дойти дальше, убить, поймать стрелу.
 */
final class Mobs {
	private Mobs() {
	}

	static final String TAG = "mobscaler:scaled";
	static final String ARROW = "mobscaler:arrow:";

	/** Радиус, с которого MobScaler начинает усиливать мобов. */
	private static final double HIKE = 3000.0;
	/** Отсюда у скелетов эффектные стрелы. */
	private static final double DEEP = 7500.0;

	static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level().dimension() != Level.OVERWORLD) {
				continue;
			}
			double distance = Math.hypot(player.getX(), player.getZ());
			if (distance >= HIKE) {
				Awards.award(player, Awards.MOB_HIKE);
			}
			if (distance >= DEEP) {
				Awards.award(player, Awards.MOB_DEEP);
			}
		}
	}

	static void onDeath(LivingEntity entity, DamageSource source) {
		ServerPlayer killer = killer(entity);
		if (killer == null) {
			return;
		}
		if (entity.entityTags().contains(TAG)) {
			Awards.award(killer, Awards.MOB_SCALED);
		}
		Entity direct = source.getDirectEntity();
		if (direct instanceof AbstractArrow arrow && Fletchers.isFletcher(arrow.getPickupItemStackOrigin())) {
			Awards.award(killer, Awards.FLETCHER_KILL);
		}
	}

	/** Стрела от усиленного скелета: моб помечен тегом эффекта, стрела принадлежит ему. */
	static void onDamage(LivingEntity victim, DamageSource source, float baseDamage, float damage, boolean blocked) {
		if (!(victim instanceof ServerPlayer player)) {
			return;
		}
		Entity direct = source.getDirectEntity();
		if (!(direct instanceof AbstractArrow arrow)) {
			return;
		}
		if (!(arrow.getOwner() instanceof AbstractSkeleton skeleton)) {
			return;
		}
		for (String tag : skeleton.entityTags()) {
			if (tag.startsWith(ARROW)) {
				Awards.award(player, Awards.MOB_ARROW);
				return;
			}
		}
	}

	private static ServerPlayer killer(LivingEntity entity) {
		Entity credit = entity.getKillCredit();
		if (credit instanceof ServerPlayer player) {
			return player;
		}
		if (entity.getLastHurtByMob() instanceof ServerPlayer player) {
			return player;
		}
		return null;
	}
}
