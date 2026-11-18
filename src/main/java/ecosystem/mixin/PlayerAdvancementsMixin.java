package ecosystem.mixin;

import ecosystem.Awards;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * «Свояк» после любой выдачи достижения.
 * <p>
 * Через {@link PlayerAdvancements#award} проходит и выдача из {@link Awards}, и ванильные триггеры
 * (`enchanted_item` у «Стола», `started_riding` у «Рысака»), поэтому капстоун не ждёт следующей
 * выдачи кода и не требует опроса игроков в тике.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
	@Shadow
	private ServerPlayer player;

	@Inject(method = "award(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z", at = @At("RETURN"))
	private void ecosystem$checkCapstone(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			Awards.onAwarded(this.player, holder);
		}
	}
}
