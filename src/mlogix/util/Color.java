package mlogix.util;

public class Color {
    // 来自 Anuken/Arc/arc-core/src/arc/graphics/Color.java
    public static int rgba8888(float r, float g, float b, float a){
        return ((int)(r * 255) << 24) | ((int)(g * 255) << 16) | ((int)(b * 255) << 8) | (int)(a * 255);
    }

    public static double toDoubleBits(float r, float g, float b, float a){
        return Double.longBitsToDouble(Color.rgba8888(r, g, b, a) & 0x00000000_ffffffffL);
    }

    public static double toDoubleBits(int r, int g, int b, int a){
        return toDoubleBits(r / 255f, g / 255f, b / 255f, a / 255f);
    }
}
