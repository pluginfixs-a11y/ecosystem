package ecosystem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Экосистема: одна вкладка достижений, которая учит механикам всего набора модов.
 * <p>
 * Мод ничего не регистрирует в мире и не трогает соседние моды: он наблюдает за тем, что игрок
 * делает ванильными предметами и блоками, и выдаёт критерий нужного достижения. Наблюдение
 * событийное: в тик попадают только дешёвые проверки, тяжёлые разборы запускаются по клику или
 * по факту события.
 * <p>
 * Полностью серверный: клиенту ставить не надо, весь текст достижений — русский, в самих файлах.
 */
public class Ecosystem implements ModInitializer {
	public static final String MOD_ID = "ecosystem";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** {@code -Decosystem.debug=true} — писать в лог каждое выданное достижение. */
	public static final boolean DEBUG = Boolean.getBoolean("ecosystem.debug");
	/** Стол лучника — обычный блок: без мода не на что вешать достижение. */
	public static final boolean FLETCHER_LOADED = FabricLoader.getInstance().isModLoaded("fletcher");
	/** Ветка рыбалки живёт на тегах brine: без него её ноды не выдаются, а «Свояк» её не ждёт. */
	public static final boolean BRINE_LOADED = FabricLoader.getInstance().isModLoaded("brine");
	/** Аномалия держит своё дерево: «Свояк» ждёт её ноду, но без мода не ждёт. */
	public static final boolean ANOMALY_LOADED = FabricLoader.getInstance().isModLoaded("anomaly");

	/** Загружен ли мод набора: «Свояк» ждёт только те ветки, чьи моды стоят на сервере. */
	public static boolean loaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register(Ticker::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(SelfCheck::run);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
			// При неудачной перезагрузке реестр в промежуточном состоянии — не пугаем логом.
			if (success) {
				SelfCheck.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Ticker.clear());

		ServerLivingEntityEvents.AFTER_DEATH.register(Mobs::onDeath);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Rockets::onDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Mobs::onDamage);
		ServerEntityEvents.ENTITY_LOAD.register(Rockets::onLoad);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> Fletchers.onLoad(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register(Rockets::onUnload);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> Fletchers.onUnload(entity));
		// Железная стрела ломает блок и уничтожается; событие ломания приходит и на кирку,
		// поэтому внутри проверяется, что стрела именно летела.
		PlayerBlockBreakEvents.AFTER.register(Fletchers::onBlockBreak);

		UseEntityCallback.EVENT.register(Steeds::onUseEntity);
		UseItemCallback.EVENT.register(Steeds::onUseItem);

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Warps.onRespawn(newPlayer));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.player;
			if (player == null) {
				return;
			}
			UUID id = player.getUUID();
			Warps.forget(id);
			Carts.forget(id);
			Steeds.forget(player);
			Rockets.forget(player);
		});

		LOGGER.info("Ecosystem: вкладка достижений активна (debug={}, fletcher={}, brine={}, anomaly={})", DEBUG,
				FLETCHER_LOADED, BRINE_LOADED, ANOMALY_LOADED);
	}
}
