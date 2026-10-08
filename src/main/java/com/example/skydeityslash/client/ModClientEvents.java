package com.example.skydeityslash.client;

import com.example.skydeityslash.client.objopt.ObjOpt;
import com.example.skydeityslash.item.BladeCharge;
import com.example.skydeityslash.registry.ModBlockEntities;
import com.example.skydeityslash.registry.ModEntities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端事件：注册自定义实体与方块实体的渲染器。
 */
public class ModClientEvents {

    /**
     * 退出世界时清理刀模渲染优化的显存缓存与 NBT 解析缓存。
     *
     * <p>顶点缓冲是 GL 对象，随图形上下文存在，跨世界复用没有意义；
     * 不清的话反复进出世界会让缓存一直握着已经没用的缓冲。
     */
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ObjOpt.onLogout();
    }

    /**
     * **Ctrl 切换充能条是否显示**（左右 Ctrl 都行）。
     *
     * <p>用 Forge 的 {@link InputEvent.Key}（它挂在 {@code KeyboardHandler.keyPress} 的末尾，
     * **界面打开时也会触发**）而不是 {@code KeyMapping} —— 因为"想看清物品图标"这件事多半发生在
     * 背包界面里，而 KeyMapping 在界面打开时收不到点击。
     * 自动重复是 {@code GLFW_REPEAT}，被 {@code PRESS} 过滤掉，所以按住只会切一次。
     *
     * <p>★ 键位固定在 Ctrl、不能在选项里改（要可改键就换 KeyMapping）。
     * 2026-10-06 由 Shift 改为 Ctrl：**Shift 同时是潜行键**，原来每次潜行都会跟着切一次。
     * Ctrl 也还是有几个原版用途（疾跑、丢整叠），但都要求"同时按别的键"，单击 Ctrl 基本是空档。
     */
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        int key = event.getKey();
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            ChargeBar.visible = !ChargeBar.visible;
        }
    }

    /**
     * 手持**充能已满**的拔刀剑时，在玩家身后画该刀自己的那圈法环。
     *
     * <p>条件是「手里拿着哪把刀」+ 纯视觉 ⇒ 完全客户端：不生成实体、不做同步，别人身上的环
     * 也由各自客户端在同一事件里画出来（其他玩家的手持物品本来就是同步的）。
     * 第一人称看不到自己（自身模型不渲染，事件不触发），F5 / 别人的视角才有。
     *
     * <p>★ 各刀的环**不做抽象**（造型不同，一把刀一个类）。目前除芙宁娜外都还没有自己的环，
     * 先**统一借用 `FurinaHalo`**；以后给某把刀做了自己的环，就在这里按刀名分支。
     */
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        BladeCharge.Config cfg = BladeCharge.configOf(held);
        if (cfg == null || !BladeCharge.isFull(held)) return;
        // ★ 环**每把刀一个类**（造型各不相同），这里按刀定义名分发；还没做环的刀就先不画。
        switch (BladeCharge.bladeName(held)) {
            case "slash_furina" -> FurinaHalo.render(
                    event.getPoseStack(), event.getMultiBufferSource(), player, event.getPartialTick());
            case "sakurafox" -> SakurafoxHalo.render(
                    event.getPoseStack(), event.getMultiBufferSource(), player, event.getPartialTick());
            case "zankou" -> ZankouHalo.render(
                    event.getPoseStack(), event.getMultiBufferSource(), player, event.getPartialTick());
            default -> { }
        }
    }

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.UMBRELLA.get(), RenderRainUmbrella::new);
        event.registerEntityRenderer(ModEntities.INK_FOX_FIELD.get(), RenderInkFoxField::new);
        event.registerEntityRenderer(ModEntities.GOLDEN_BRANCH_BALL.get(), RenderGoldenBranchBall::new);
        event.registerEntityRenderer(ModEntities.FURINA_NPC.get(), RenderFurinaNpc::new);
        event.registerEntityRenderer(ModEntities.HUTAO_NPC.get(), RenderHutaoNpc::new);
        event.registerEntityRenderer(ModEntities.ODETTE_NPC.get(), RenderOdetteNpc::new);
        event.registerEntityRenderer(ModEntities.LINNEA_NPC.get(), RenderLinneaNpc::new);
        event.registerEntityRenderer(ModEntities.COLUMBINA_NPC.get(), RenderColumbinaNpc::new);
        event.registerEntityRenderer(ModEntities.IROI_NPC.get(), RenderIroiNpc::new);
        event.registerEntityRenderer(ModEntities.TIANXING_FAZ.get(), RenderTianxingFaz::new);
        event.registerEntityRenderer(ModEntities.TIANXING_STONE.get(), RenderTianxingStone::new);
        event.registerEntityRenderer(ModEntities.SWORD_HOLOGRAM.get(), RenderSwordHologram::new);
        event.registerEntityRenderer(ModEntities.GHOST_BUTTERFLY.get(), RenderGhostButterfly::new);
        event.registerEntityRenderer(ModEntities.XUANFENG_RING.get(), RenderXuanfengRing::new);
        event.registerEntityRenderer(ModEntities.EFFECT_PREVIEW.get(), RenderEffectPreview::new);
        event.registerEntityRenderer(ModEntities.CHIKUI_ARC.get(), RenderChikuiArc::new);
        event.registerEntityRenderer(ModEntities.CHIKUI_FLOWER.get(), RenderChikuiFlower::new);
        event.registerEntityRenderer(ModEntities.IROI_BEAM.get(), RenderIroiBeam::new);
        event.registerEntityRenderer(ModEntities.IROI_ORB.get(), RenderIroiOrb::new);
        event.registerEntityRenderer(ModEntities.BLOOM_SLASH.get(), RenderBloomSlash::new);
        event.registerEntityRenderer(ModEntities.SAKURA_BLOOM.get(), RenderSakuraBloom::new);
        event.registerEntityRenderer(ModEntities.CUT_LINES.get(), RenderCutLines::new);
        event.registerEntityRenderer(ModEntities.NEW_MOON_RULE.get(), RenderNewMoonRule::new);
        event.registerEntityRenderer(ModEntities.OVERRANK_MAGIC.get(), RenderOverrankMagic::new);

        event.registerBlockEntityRenderer(ModBlockEntities.ARCANE_PEDESTAL.get(), ArcanePedestalBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ENCHANTING_APPARATUS.get(), EnchantingApparatusBlockEntityRenderer::new);
    }
}
