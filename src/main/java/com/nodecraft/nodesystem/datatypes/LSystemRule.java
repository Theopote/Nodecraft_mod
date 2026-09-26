package com.nodecraft.nodesystem.datatypes;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * L-系统规则数据类型，表示一个L-系统的生产规则
 *
 * @param symbol      规则应用的符号（如 "F"）
 * @param production  符号如何展开的字符串（如 "F[+F]F[-F]F"）
 * @param probability 规则应用的概率（0-1，用于随机L-系统）
 * @param context     L-系统上下文敏感规则（可选）
 */
public record LSystemRule(String symbol, String production, float probability, String context) {
    /**
     * 创建一个L-系统规则
     *
     * @param symbol      规则应用的符号
     * @param production  符号展开的字符串
     * @param probability 规则应用的概率（0-1）
     * @param context     上下文敏感规则（可选）
     */
    public LSystemRule(String symbol, String production, float probability, String context) {
        this.symbol = symbol != null ? symbol : "";
        this.production = production != null ? production : "";
        this.probability = Math.max(0.0f, Math.min(1.0f, probability));
        this.context = context;
    }

    /**
     * 创建一个L-系统规则（简化版本，概率为1.0，无上下文）
     *
     * @param symbol     规则应用的符号
     * @param production 符号展开的字符串
     */
    public LSystemRule(String symbol, String production) {
        this(symbol, production, 1.0f, null);
    }

    /**
     * 创建一个L-系统规则（带概率，无上下文）
     *
     * @param symbol      规则应用的符号
     * @param production  符号展开的字符串
     * @param probability 规则应用的概率
     */
    public LSystemRule(String symbol, String production, float probability) {
        this(symbol, production, probability, null);
    }

    /**
     * 获取规则符号
     *
     * @return 规则符号
     */
    @Override
    public String symbol() {
        return symbol;
    }

    /**
     * 获取生产字符串
     *
     * @return 生产字符串
     */
    @Override
    public String production() {
        return production;
    }

    /**
     * 获取规则概率
     *
     * @return 规则概率
     */
    @Override
    public float probability() {
        return probability;
    }

    /**
     * 获取上下文字符串
     *
     * @return 上下文字符串，可能为null
     */
    @Override
    public String context() {
        return context;
    }

    /**
     * 检查规则是否有上下文
     *
     * @return 如果有上下文返回true
     */
    public boolean hasContext() {
        return context != null && !context.trim().isEmpty();
    }

    /**
     * 检查规则是否为概率规则
     *
     * @return 如果概率小于1.0返回true
     */
    public boolean isProbabilistic() {
        return probability < 1.0f;
    }

    @Override
    public @NonNull String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(symbol).append(" -> ").append(production);
        if (isProbabilistic()) {
            sb.append(" (p=").append(probability).append(")");
        }
        if (hasContext()) {
            sb.append(" [context: ").append(context).append("]");
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        LSystemRule that = (LSystemRule) obj;

        return Float.compare(that.probability, probability) == 0 &&
                symbol.equals(that.symbol) &&
                production.equals(that.production) &&
                (Objects.equals(context, that.context));
    }

}