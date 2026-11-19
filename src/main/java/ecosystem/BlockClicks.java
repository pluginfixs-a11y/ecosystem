package ecosystem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Правый клик по блоку, у которого три мода меняют исход:
 * поле (hoefarm), котёл (cauldronwash), верёвка (ladderhang).
 * <p>
 * Снимок «до» берётся на входе пакета ({@code handleUseItemOn}), разбор — концом следующего тика.
 * Так не важно, в каком порядке сработали обработчики модов и отменили ли они ванильный клик:
 * сравниваются только реальные состояния блоков.
 * <p>
 * Каждая нода — своё действие: сбор, вспашка и посев считаются отдельно, поэтому одно нажатие
 * мотыгой не выдаёт два достижения.
 */
public final class BlockClicks {
	/** Радиус поля по X/Z: незерит берёт 5×5, плюс запас. */
	private static final int R = 2;
	private static final int DOWN = 3;
	private static final int UP = 2;
	private static final int LAYERS = DOWN + UP + 1;
	private static final int FIELD_SIZE = (R * 2 + 1) * (R * 2 + 1) * LAYERS;

	/** Ткани: шерсть, ковёр, кровать, бетон и его порошок. */
	private static final Set<String> FABRIC = Set.of("wool", "carpet", "bed", "concrete", "concrete_powder");
	/** Всё остальное, что котёл тоже стирает. */
	private static final Set<String> EXOTIC = Set.of(
			"stained_glass", "stained_glass_pane", "glazed_terracotta", "terracotta",
			"candle", "bundle", "harness");

	/** Порядок важен: light_blue раньше blue, light_gray раньше gray. */
	private static final String[] COLORS = {
			"light_blue", "light_gray", "white", "orange", "magenta", "yellow", "lime", "pink",
			"gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
	};

	/** Пороги за один клик. Ваниль меняет ровно один блок, поэтому порог 2 — уже мод. */
	private static final int HARVEST = 3;
	private static final int TILL = 6;
	private static final int SOW = 5;
	private static final int ROPE_LONG = 16;

	private static final List<Snap> PENDING = new ArrayList<>(4);

	private BlockClicks() {
	}

	/** Вызывается из миксина до того, как клик обработают моды. */
	public static void onClick(ServerPlayer player, ServerLevel level, ItemStack stack, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		// Координаты приходят из пакета: без этой проверки сервер читал бы мир и грузил чанки
		// по любой точке, которую пришлёт клиент (ваниль отсекает такое уже позже).
		if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > 36.0) {
			return;
		}
		BlockState state = level.getBlockState(pos);

		if (state.is(Blocks.FLETCHING_TABLE)) {
			// Те же условия, что у fletcher 1.2: главная рука, не наблюдатель; шифт не мешает,
			// если в руке не блок (тогда мод отдаёт клик ванили на установку).
			if (!Ecosystem.FLETCHER_LOADED || hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
				return;
			}
			if (player.isSecondaryUseActive() && stack.getItem() instanceof BlockItem) {
				return;
			}
			Awards.award(player, Awards.FLETCHER_TABLE);
			return;
		}

		if (state.is(Blocks.STRUCTURE_VOID)) {
			PENDING.add(new Snap(Kind.MARKER, player, level, hand, stack.getItem(), pos, null, null));
			return;
		}

		if (stack.is(Items.PHANTOM_MEMBRANE) && state.getBlock() instanceof SignBlock) {
			PENDING.add(new Snap(Kind.SIGN, player, level, hand, stack.getItem(), pos, null, null));
			return;
		}

		if (state.is(Blocks.WATER_CAULDRON)) {
			PENDING.add(new Snap(Kind.CAULDRON, player, level, hand, stack.getItem(), pos, null, null));
			return;
		}

		if (stack.is(Items.LADDER) && state.is(Blocks.LADDER)) {
			Direction facing = state.getValue(LadderBlock.FACING);
			BlockPos bottom = pos;
			for (int guard = 0; guard < 512; guard++) {
				BlockPos below = bottom.below();
				if (!level.isLoaded(below)) {
					break;
				}
				BlockState belowState = level.getBlockState(below);
				if (!belowState.is(Blocks.LADDER) || belowState.getValue(LadderBlock.FACING) != facing) {
					break;
				}
				bottom = below;
			}
			PENDING.add(new Snap(Kind.LADDER, player, level, hand, stack.getItem(), pos, bottom.below(), null));
			return;
		}

		// Поле снимаем только с мотыгой; пустая рука — лишь по грядке (bare_hand_harvest),
		// иначе любой ПКМ по траве стоил бы 150 чтений мира.
		if (!stack.is(ItemTags.HOES) && !(stack.isEmpty() && farmish(state))) {
			return;
		}
		if (!farmish(state) && !state.canBeReplaced()) {
			return;
		}
		BlockState[] before = new BlockState[FIELD_SIZE];
		int i = 0;
		for (int dy = -DOWN; dy <= UP; dy++) {
			for (int dx = -R; dx <= R; dx++) {
				for (int dz = -R; dz <= R; dz++) {
					BlockPos sample = pos.offset(dx, dy, dz);
					// За границей загруженного мира не читаем: иначе клик у края прогрузки
					// тянул бы за собой синхронную загрузку чанков.
					before[i++] = level.isLoaded(sample) ? level.getBlockState(sample) : null;
				}
			}
		}
		PENDING.add(new Snap(Kind.FIELD, player, level, hand, stack.getItem(), pos, null, before));
	}

	/** Удар по маркеру таблички: снимок тот же, что и у правого клика. */
	public static void onMarkerPunch(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > 36.0) {
			return;
		}
		PENDING.add(new Snap(Kind.MARKER, player, level, InteractionHand.MAIN_HAND, Items.AIR, pos, null, null));
	}

	public static void flush(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		for (int i = PENDING.size() - 1; i >= 0; i--) {
			Snap snap = PENDING.get(i);
			// Снимок разбирается на следующем тике: к этому моменту пакет точно обработан.
			if (snap.born >= now) {
				continue;
			}
			PENDING.remove(i);
			if (!snap.player.isRemoved()) {
				snap.evaluate();
			}
		}
	}

	static void clear() {
		PENDING.clear();
	}

	private enum Kind { FIELD, LADDER, CAULDRON, SIGN, MARKER }

	private static final class Snap {
		final Kind kind;
		final ServerPlayer player;
		final ServerLevel level;
		final InteractionHand hand;
		final Item item;
		final BlockPos pos;
		final BlockPos target;
		final BlockState[] before;
		final int born;

		Snap(Kind kind, ServerPlayer player, ServerLevel level, InteractionHand hand, Item item,
		     BlockPos pos, BlockPos target, BlockState[] before) {
			this.kind = kind;
			this.player = player;
			this.level = level;
			this.hand = hand;
			this.item = item;
			this.pos = pos;
			this.target = target;
			this.before = before;
			this.born = level.getServer().getTickCount();
		}

		void evaluate() {
			switch (kind) {
				case FIELD -> field();
				case LADDER -> ladder();
				case CAULDRON -> cauldron();
				case SIGN -> sign();
				case MARKER -> marker();
			}
		}

		/** Табличка с мембраной: на её месте остался невидимый маркер — доска ушла в текст. */
		private void sign() {
			if (level.getBlockState(pos).is(Blocks.STRUCTURE_VOID)) {
				Awards.award(player, Awards.GHOST_SIGN);
			}
		}

		/** Маркер под ударом вернул табличку на место. */
		private void marker() {
			if (level.getBlockState(pos).getBlock() instanceof SignBlock) {
				Awards.award(player, Awards.GHOST_TEXT_BACK);
			}
		}

		private void field() {
			int harvested = 0;
			int tilled = 0;
			int sown = 0;
			int i = 0;
			for (int dy = -DOWN; dy <= UP; dy++) {
				for (int dx = -R; dx <= R; dx++) {
					for (int dz = -R; dz <= R; dz++) {
						BlockState was = before[i++];
						if (was == null) {
							continue;
						}
						BlockPos sample = pos.offset(dx, dy, dz);
						if (!level.isLoaded(sample)) {
							continue;
						}
						BlockState now = level.getBlockState(sample);
						if (was.equals(now)) {
							continue;
						}
						if (ripe(was) && !ripe(now)) {
							harvested++;
						} else if (tillable(was) && now.is(Blocks.FARMLAND)) {
							tilled++;
						} else if (was.isAir() && now.getBlock() instanceof CropBlock) {
							sown++;
						}
					}
				}
			}
			if (harvested >= HARVEST) {
				Awards.award(player, Awards.FIELD);
			}
			if (tilled >= TILL) {
				Awards.award(player, Awards.FIELD_TILL);
			}
			if (sown >= SOW) {
				Awards.award(player, Awards.FIELD_SOW);
			}
		}

		private void ladder() {
			BlockState lower = level.getBlockState(target);
			if (!lower.is(Blocks.LADDER)) {
				return;
			}
			Direction facing = lower.getValue(LadderBlock.FACING);
			int length = 0;
			BlockPos cursor = target;
			for (int guard = 0; guard < 512; guard++) {
				BlockState state = level.getBlockState(cursor);
				if (!state.is(Blocks.LADDER) || state.getValue(LadderBlock.FACING) != facing) {
					break;
				}
				length++;
				cursor = cursor.above();
			}
			if (length >= ROPE_LONG) {
				Awards.award(player, Awards.LADDER_LONG);
			} else {
				Awards.award(player, Awards.LADDER);
			}
		}

		private void cauldron() {
			ItemStack now = player.getItemInHand(hand);
			if (now.isEmpty() || now.getItem() == item) {
				return;
			}
			String family = family(BuiltInRegistries.ITEM.getKey(now.getItem()).getPath());
			if (FABRIC.contains(family)) {
				Awards.award(player, Awards.CAULDRON);
			} else if (EXOTIC.contains(family)) {
				Awards.award(player, Awards.CAULDRON_ALL);
			}
		}
	}

	/** «white_bed» -> «bed», «white_stained_glass» -> «stained_glass». */
	private static String family(String path) {
		for (String color : COLORS) {
			if (path.startsWith(color)) {
				int at = color.length();
				if (path.length() > at && path.charAt(at) == '_') {
					return path.substring(at + 1);
				}
			}
		}
		return path;
	}

	private static boolean farmish(BlockState state) {
		return state.getBlock() instanceof CropBlock
				|| state.getBlock() instanceof NetherWartBlock
				|| state.getBlock() instanceof FarmlandBlock
				|| tillable(state);
	}

	/** Ванильная карта HoeItem.TILLABLES, которую использует hoefarm. */
	private static boolean tillable(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.DIRT_PATH)
				|| state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT);
	}

	private static boolean ripe(BlockState state) {
		if (state.getBlock() instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		if (state.getBlock() instanceof NetherWartBlock) {
			return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
		}
		return false;
	}
}
