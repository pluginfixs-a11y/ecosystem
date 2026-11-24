package ecosystem.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@code AbstractArrow#isInGround} — {@code protected}, а он единственный отличает стрелу, которая воткнулась
 * в блок именно сейчас (и потому могла его сломать), от стрелы, которая просто пролетает мимо.
 * Ванильный {@code onHitBlock} ставит флаг до обработки попадания, поэтому в момент ломания он уже {@code true}.
 */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
	@Invoker("isInGround")
	boolean ecosystem$inGround();
}
