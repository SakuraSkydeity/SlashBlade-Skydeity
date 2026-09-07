package com.example.skydeityslash.registry;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.effect.BloodPlumEffect;
import com.example.skydeityslash.effect.LumiEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 自定义状态效果注册表。
 */
public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, SkydeitySlash.MODID);

    /** 血梅香：每级每秒造成 10 点伤害，等级为 5 的倍数时爆炸扣除 50% 最大生命 */
    public static final RegistryObject<MobEffect> BLOOD_PLUM =
            EFFECTS.register("blood_plum", BloodPlumEffect::new);

    /** 露米：仙乡的赠别礼带来的增益，持续持有可累计攻击力提升 */
    public static final RegistryObject<MobEffect> LUMI =
            EFFECTS.register("lumi", LumiEffect::new);
}
