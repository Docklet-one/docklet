package com.docklet.shell.ui;

/** module.json 里的 glyph 名字 → 图标类型。只在这里维护一份。 */
public final class ModuleGlyph {

    private ModuleGlyph() {
    }

    public static IconView.Type of(String glyph) {
        if (glyph == null || glyph.isEmpty()) return IconView.Type.SPARK;
        switch (glyph) {
            case "terminal": return IconView.Type.LIST;
            case "list": return IconView.Type.LIST;
            case "timer": return IconView.Type.TIMER;
            case "brush": return IconView.Type.BRUSH;
            case "calc": return IconView.Type.CALC;
            case "network": return IconView.Type.NETWORK;
            case "game": return IconView.Type.GAME;
            case "music": return IconView.Type.MUSIC;
            case "leaf": return IconView.Type.LEAF;
            case "book": return IconView.Type.BOOK;
            case "camera": return IconView.Type.CAMERA;
            case "chat": return IconView.Type.CHAT;
            case "image": return IconView.Type.IMAGE;
            case "shield": return IconView.Type.SHIELD;
            case "spark": return IconView.Type.SPARK;
            default: return IconView.Type.SPARK;
        }
    }
}
