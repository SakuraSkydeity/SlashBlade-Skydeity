package com.example.skydeityslash.client.objopt;

import java.lang.reflect.Method;
import net.minecraftforge.fml.ModList;

/**
 * 光影包状态查询。
 *
 * <p>对 Oculus / Iris 只走反射，不产生硬依赖 —— 没装光影时这些类根本不存在。
 *
 * <p>需要区分两件事：
 * <ul>
 *   <li><b>是否正在渲染阴影贴图</b>：此时直接回退原版路径（本模块不做阴影简化）；</li>
 *   <li><b>是否启用了光影包</b>：启用时关掉静态缓冲这一层。光影会替换 vanilla shader，
 *       静态缓冲"光照与法线变换可预测"的假设不再成立，但立即写这一层继续可用。</li>
 * </ul>
 */
public final class ShaderPackCompat {

    private static final long CACHE_WINDOW_MS = 250L;

    private static volatile long lastShaderPackCheck;
    private static volatile boolean shaderPackInUse;

    private static boolean resolved;
    private static Object apiInstance;
    private static Method isShaderPackInUseMethod;
    private static Method isRenderingShadowPassMethod;

    private ShaderPackCompat() {
    }

    public static boolean isShaderPackInUse() {
        long now = System.currentTimeMillis();
        if (now - lastShaderPackCheck <= CACHE_WINDOW_MS) {
            return shaderPackInUse;
        }
        shaderPackInUse = query(false);
        lastShaderPackCheck = now;
        return shaderPackInUse;
    }

    public static boolean isRenderingShadowPass() {
        return query(true);
    }

    /** 静态缓冲这一层是否可用：光影包启用时不冒险。 */
    public static boolean allowsStaticBuffer() {
        return !isShaderPackInUse();
    }

    private static boolean query(boolean shadowPass) {
        try {
            resolve();
            Method method = shadowPass ? isRenderingShadowPassMethod : isShaderPackInUseMethod;
            return method != null && Boolean.TRUE.equals(method.invoke(apiInstance));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            ModList modList = ModList.get();
            if (modList == null
                    || (!modList.isLoaded("oculus") && !modList.isLoaded("iris"))) {
                return;
            }
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            if (instance == null) {
                return;
            }
            apiInstance = instance;
            isShaderPackInUseMethod = api.getMethod("isShaderPackInUse");
            isRenderingShadowPassMethod = api.getMethod("isRenderingShadowPass");
        } catch (Throwable ignored) {
            apiInstance = null;
            isShaderPackInUseMethod = null;
            isRenderingShadowPassMethod = null;
        }
    }
}
