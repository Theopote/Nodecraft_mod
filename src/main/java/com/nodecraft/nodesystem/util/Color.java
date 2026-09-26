package com.nodecraft.nodesystem.util;

/**
 * 颜色类，表示RGBA颜色
 */
public record Color(float red, float green, float blue, float alpha) {

    public static final Color WHITE = new Color(1.0f, 1.0f, 1.0f, 1.0f);
    public static final Color BLACK = new Color(0.0f, 0.0f, 0.0f, 1.0f);
    public static final Color RED = new Color(1.0f, 0.0f, 0.0f, 1.0f);
    public static final Color GREEN = new Color(0.0f, 1.0f, 0.0f, 1.0f);
    public static final Color BLUE = new Color(0.0f, 0.0f, 1.0f, 1.0f);
    public static final Color YELLOW = new Color(1.0f, 1.0f, 0.0f, 1.0f);
    public static final Color CYAN = new Color(0.0f, 1.0f, 1.0f, 1.0f);
    public static final Color MAGENTA = new Color(1.0f, 0.0f, 1.0f, 1.0f);
    public static final Color TRANSPARENT = new Color(0.0f, 0.0f, 0.0f, 0.0f);

    /**
     * 创建一个新的颜色
     *
     * @param red   红色分量 (0-1)
     * @param green 绿色分量 (0-1)
     * @param blue  蓝色分量 (0-1)
     * @param alpha 透明度 (0-1, 0为完全透明)
     */
    public Color(float red, float green, float blue, float alpha) {
        this.red = clamp(red, 0.0f, 1.0f);
        this.green = clamp(green, 0.0f, 1.0f);
        this.blue = clamp(blue, 0.0f, 1.0f);
        this.alpha = clamp(alpha, 0.0f, 1.0f);
    }

    /**
     * 创建一个不透明颜色
     *
     * @param red   红色分量 (0-1)
     * @param green 绿色分量 (0-1)
     * @param blue  蓝色分量 (0-1)
     */
    public Color(float red, float green, float blue) {
        this(red, green, blue, 1.0f);
    }

    /**
     * 从整数值创建颜色 (ARGB格式)
     *
     * @param argb ARGB整数值
     */
    public Color(int argb) {
        this(
                ((argb >> 16) & 0xFF) / 255.0f,
                ((argb >> 8) & 0xFF) / 255.0f,
                (argb & 0xFF) / 255.0f,
                ((argb >> 24) & 0xFF) / 255.0f
        );
    }

    /**
     * 获取红色分量
     *
     * @return 红色分量值 (0-1)
     */
    @Override
    public float red() {
        return red;
    }

    /**
     * 获取绿色分量
     *
     * @return 绿色分量值 (0-1)
     */
    @Override
    public float green() {
        return green;
    }

    /**
     * 获取蓝色分量
     *
     * @return 蓝色分量值 (0-1)
     */
    @Override
    public float blue() {
        return blue;
    }

    /**
     * 获取透明度
     *
     * @return 透明度值 (0-1)
     */
    @Override
    public float alpha() {
        return alpha;
    }

    /**
     * 将颜色转换为ARGB整数值
     *
     * @return ARGB整数值
     */
    public int toArgb() {
        int a = (int) (alpha * 255.0f) & 0xFF;
        int r = (int) (red * 255.0f) & 0xFF;
        int g = (int) (green * 255.0f) & 0xFF;
        int b = (int) (blue * 255.0f) & 0xFF;

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 创建具有不同透明度的新颜色
     *
     * @param alpha 新的透明度值 (0-1)
     * @return 新的颜色对象
     */
    public Color withAlpha(float alpha) {
        return new Color(red, green, blue, alpha);
    }

    /**
     * 混合两种颜色
     *
     * @param other 要混合的另一种颜色
     * @param t     混合因子 (0为此颜色，1为other颜色)
     * @return 混合后的新颜色
     */
    public Color blend(Color other, float t) {
        float clampedT = clamp(t, 0.0f, 1.0f);
        return new Color(
                lerp(red, other.red, clampedT),
                lerp(green, other.green, clampedT),
                lerp(blue, other.blue, clampedT),
                lerp(alpha, other.alpha, clampedT)
        );
    }

    /**
     * 从HSV值创建颜色
     *
     * @param hue        色相 (0-360)
     * @param saturation 饱和度 (0-1)
     * @param value      明度 (0-1)
     * @param alpha      透明度 (0-1)
     * @return 颜色对象
     */
    public static Color fromHSV(float hue, float saturation, float value, float alpha) {
        hue = ((hue % 360) + 360) % 360;
        saturation = clamp(saturation, 0.0f, 1.0f);
        value = clamp(value, 0.0f, 1.0f);

        float c = value * saturation;
        float x = c * (1 - Math.abs((hue / 60) % 2 - 1));
        float m = value - c;

        float r = 0, g = 0, b = 0;

        if (hue < 60) {
            r = c;
            g = x;
        } else if (hue < 120) {
            r = x;
            g = c;
        } else if (hue < 180) {
            g = c;
            b = x;
        } else if (hue < 240) {
            g = x;
            b = c;
        } else if (hue < 300) {
            r = x;
            b = c;
        } else {
            r = c;
            b = x;
        }

        return new Color(r + m, g + m, b + m, alpha);
    }

    /**
     * 从十六进制字符串创建颜色
     *
     * @param hex 十六进制颜色字符串 (#RRGGBB 或 #AARRGGBB)
     * @return 颜色对象，如果解析失败则返回黑色
     */
    public static Color fromHex(String hex) {
        try {
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            }

            if (hex.length() == 6) {
                // #RRGGBB格式
                int r = Integer.parseInt(hex.substring(0, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 4), 16);
                int b = Integer.parseInt(hex.substring(4, 6), 16);
                return new Color(r / 255.0f, g / 255.0f, b / 255.0f, 1.0f);
            } else if (hex.length() == 8) {
                // #AARRGGBB格式
                int a = Integer.parseInt(hex.substring(0, 2), 16);
                int r = Integer.parseInt(hex.substring(2, 4), 16);
                int g = Integer.parseInt(hex.substring(4, 6), 16);
                int b = Integer.parseInt(hex.substring(6, 8), 16);
                return new Color(r / 255.0f, g / 255.0f, b / 255.0f, a / 255.0f);
            }
        } catch (Exception e) {
            // 解析失败
        }

        return BLACK;
    }

    @Override
    public String toString() {
        return String.format("Color(%.2f, %.2f, %.2f, %.2f)", red, green, blue, alpha);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Color other = (Color) obj;
        return Float.compare(other.red, red) == 0 &&
                Float.compare(other.green, green) == 0 &&
                Float.compare(other.blue, blue) == 0 &&
                Float.compare(other.alpha, alpha) == 0;
    }

    // 工具方法

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
} 