package ecosystem;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * Стрелы со стола лучника (fletcher). Своей стрелы мод завести не может — это ванильная
 * {@code minecraft:arrow} с составом в {@code custom_data.fletcher}, поэтому «своя стрела»
 * проверяется по этому ключу. Каждую секунду, по инвентарю игроков: 41 слот — копейки.
 */
final class Fletchers {
	private Fletchers() {
	}

	static final String ROOT = "fletcher";

	static void tick(MinecraftServer server) {
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
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(ROOT);
	}
}
