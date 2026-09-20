package com.example.skydeityslash.client.objopt;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * 记录当前正在渲染的刀处于哪个展示上下文。
 *
 * <p>为什么需要它：静态缓冲里烘焙的是固定光照值。物品栏图标、展示框里的刀
 * 用的是固定光照，缓存住没问题；但第一/第三人称手持时，光照会随玩家移动变化，
 * 缓存键会被不断刷出新的条目，既浪费显存又拿不到收益。所以手持一律不走静态缓冲。
 *
 * <p>{@code SlashBladeTEISR.renderByItem} 是**嵌套可重入**的（刀架里的刀会在外层
 * 渲染过程中再次进来），因此用栈而不是单个值。栈在中途异常没弹干净时，
 * 最多表现成"静态缓冲暂时不生效"，不会崩。
 */
public final class BladeDisplayContext {

    private static final ThreadLocal<Deque<ItemDisplayContext>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private BladeDisplayContext() {
    }

    public static void push(ItemDisplayContext context) {
        STACK.get().push(context);
    }

    public static void pop() {
        Deque<ItemDisplayContext> stack = STACK.get();
        stack.poll();
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    /**
     * 只有光照固定的展示上下文才允许使用带光照烘焙的静态缓冲。
     *
     * <p>栈空也算允许：那是模块内部的间接渲染，光照通常由调用方自己定住。
     */
    public static boolean allowsStaticBuffer() {
        ItemDisplayContext context = STACK.get().peek();
        return context == null
                || context == ItemDisplayContext.FIXED
                || context == ItemDisplayContext.GUI;
    }
}
