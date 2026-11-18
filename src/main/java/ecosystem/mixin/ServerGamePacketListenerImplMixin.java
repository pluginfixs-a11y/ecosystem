package ecosystem.mixin;

import ecosystem.BlockClicks;
import ecosystem.Frames;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Снимки «до» для кликов, которые меняют блок или сущность.
 * <p>
 * Берём на входе пакета: это гарантированно раньше, чем обработчики Fabric-событий из hoefarm,
 * cauldronwash, ladderhang, ghostframe и fletcher. Дальше состояние сравнивается следующим тиком,
 * поэтому не важно, отменили они ванильный клик или нет и в каком порядке грузились моды.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleUseItemOn", at = @At("HEAD"))
	private void ecosystem$snapshotClick(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
		ServerPlayer self = this.player;
		if (self == null || self.level().isClientSide()) {
			return;
		}
		InteractionHand hand = packet.getHand();
		BlockHitResult hit = packet.getHitResult();
		BlockClicks.onClick(self, self.level(), self.getItemInHand(hand), hand, hit);
	}

	@Inject(method = "handleInteract", at = @At("HEAD"))
	private void ecosystem$snapshotInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
		ServerPlayer self = this.player;
		if (self == null) {
			return;
		}
		// Шифт у ghostframe уходит ванили: мембрана кладётся в рамку как предмет.
		if (packet.usingSecondaryAction()) {
			return;
		}
		InteractionHand hand = packet.hand();
		if (hand == null || !self.getItemInHand(hand).is(Items.PHANTOM_MEMBRANE)) {
			return;
		}
		Entity target = self.level().getEntity(packet.entityId());
		if (target instanceof ItemFrame frame) {
			Frames.onClick(self, frame);
		}
	}

	/** Удар по маркеру таблички — второй путь восстановления (первый — правый клик). */
	@Inject(method = "handlePlayerAction", at = @At("HEAD"))
	private void ecosystem$snapshotPunch(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		ServerPlayer self = this.player;
		if (self == null) {
			return;
		}
		if (packet.getAction() != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
			return;
		}
		if (!self.level().getBlockState(packet.getPos()).is(Blocks.STRUCTURE_VOID)) {
			return;
		}
		BlockClicks.onMarkerPunch(self, self.level(), packet.getPos());
	}
}
