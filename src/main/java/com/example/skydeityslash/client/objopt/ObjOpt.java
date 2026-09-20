package com.example.skydeityslash.client.objopt;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * 拔刀剑 OBJ 模型渲染优化的总开关。
 *
 * <p>只做数值上与原版等价的优化（加载时烘焙、显存复用、解析结果缓存），
 * 不做任何会改变画面表现的事。任何一处出问题都会让对应路径永久降级回原版渲染，
 * 最多退化成原版性能，不会崩。
 *
 * <p><b>不针对任何具体 mod 做特殊处理。</b>本模块的行为只取决于自己的开关，
 * 以及通用的图形环境状态（是否启用光影包）。与实现思路相同的其它优化 mod 共存时，
 * 由 Mixin 的 priority 决定谁先接管 —— 这是 Mixin 机制本身的性质，本模块不感知对方是谁。
 *
 * <p>启动参数 {@code -Dskydeityslash.objopt.disable=true} 可整体关闭本模块。
 */
public final class ObjOpt {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String DISABLE_PROPERTY = "skydeityslash.objopt.disable";

    private static Boolean disabled;

    private ObjOpt() {
    }

    /** 模块是否工作。 */
    public static boolean isActive() {
        return !isDisabled();
    }

    /** 资源包重载：模型会被整体换掉，旧显存与旧解析结果全部失效。 */
    public static void onResourceReload() {
        BladeVboCache.clear();
        BladeRenderResourceCache.clear();
    }

    /** 退出世界：显存随图形上下文释放，解析缓存也没有跨世界的意义。 */
    public static void onLogout() {
        BladeVboCache.clear();
        BladeRenderResourceCache.clear();
    }

    private static boolean isDisabled() {
        Boolean cached = disabled;
        if (cached == null) {
            cached = Boolean.getBoolean(DISABLE_PROPERTY);
            disabled = cached;
            if (cached) {
                LOGGER.info("[Skydeity] 刀模渲染优化已通过启动参数关闭");
            }
        }
        return cached;
    }
}
