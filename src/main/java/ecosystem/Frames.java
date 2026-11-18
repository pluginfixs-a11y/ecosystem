package ecosystem;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;

import java.util.ArrayList;
import java.util.List;

/**
 * Рамки (ghostframe): мембрана фантома переключает невидимость рамки.
 * <p>
 * Снимок «до» берётся на входе пакета {@code handleInteract}, разбор — следующим тиком:
 * так видно не факт клика, а реальный результат — рамка спряталась или вернулась.
 */
public final class Frames {
	private Frames() {
	}

	private static final class Snap {
		final ServerPlayer player;
		final ItemFrame frame;
		final boolean hidden;
		final int born;

		Snap(ServerPlayer player, ItemFrame frame, int born) {
			this.player = player;
			this.frame = frame;
			this.hidden = frame.isInvisible();
			this.born = born;
		}
	}

	private static final List<Snap> PENDING = new ArrayList<>(2);

	public static void onClick(ServerPlayer player, ItemFrame frame) {
		PENDING.add(new Snap(player, frame, player.level().getServer().getTickCount()));
	}

	static void flush(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		for (int i = PENDING.size() - 1; i >= 0; i--) {
			Snap snap = PENDING.get(i);
			if (snap.born >= now) {
				continue;
			}
			PENDING.remove(i);
			if (snap.player.isRemoved() || snap.frame.isRemoved()) {
				continue;
			}
			boolean hidden = snap.frame.isInvisible();
			if (hidden == snap.hidden) {
				continue;
			}
			if (hidden) {
				Awards.award(snap.player, Awards.GHOST_HIDE);
			} else {
				Awards.award(snap.player, Awards.GHOST_SHOW);
			}
		}
	}

	static void clear() {
		PENDING.clear();
	}
}
