package com.example.skydeityslash.registry;

import com.example.skydeityslash.SkydeitySlash;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 自定义音效注册表。
 */
public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, SkydeitySlash.MODID);

    /** sakurafox 雷刃 SA「负心者当诛」 */
    public static final RegistryObject<SoundEvent> FUXINZHE_DANGZHU =
            SOUNDS.register("fuxinzhe_dangzhu", () ->
                    SoundEvent.createVariableRangeEvent(new ResourceLocation(SkydeitySlash.MODID, "fuxinzhe_dangzhu")));

    /** 伞特效·令花神原地（W）「烟过无痕迹」 */
    public static final RegistryObject<SoundEvent> YANGUO_WUHENJI =
            SOUNDS.register("yanguo_wuhenji", () ->
                    SoundEvent.createVariableRangeEvent(new ResourceLocation(SkydeitySlash.MODID, "yanguo_wuhenji")));

    /** 伞特效·令花神推伞（A）「笛声喑哑亦可生花」 */
    public static final RegistryObject<SoundEvent> DISHENG_SHENGHUA =
            SOUNDS.register("disheng_shenghua", () ->
                    SoundEvent.createVariableRangeEvent(new ResourceLocation(SkydeitySlash.MODID, "disheng_shenghua")));

    /** 伞特效·芙蓉花环绕（S）「幻鬼哀仙系何情」 */
    public static final RegistryObject<SoundEvent> HUANGUI_HEQING =
            SOUNDS.register("huangui_heqing", () ->
                    SoundEvent.createVariableRangeEvent(new ResourceLocation(SkydeitySlash.MODID, "huangui_heqing")));

    /** 伞特效·投伞（D）「笛烟化魂」 */
    public static final RegistryObject<SoundEvent> DIYAN_HUAHUN =
            SOUNDS.register("diyan_huahun", () ->
                    SoundEvent.createVariableRangeEvent(new ResourceLocation(SkydeitySlash.MODID, "diyan_huahun")));
}