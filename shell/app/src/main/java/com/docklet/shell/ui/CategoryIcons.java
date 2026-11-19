package com.docklet.shell.ui;

/** Curated category icon keys, drawn by {@link IconView}. */
public final class CategoryIcons {

    public static final String DEFAULT = "folder";
    public static final String HOME = "home";
    public static final String INBOX = "inbox";

    public static final String[] KEYS = {
            "star", "heart", "music", "game", "book", "folder",
            "image", "chat", "camera", "clock", "list", "calc",
            "brush", "globe", "spark", "leaf", "work", "flask",
            "grid"
    };

    private CategoryIcons() {
    }

    public static int backgroundOf(String key) {
        if (key == null) return 0xFFF0EADF;
        switch (key) {
            case "star": return 0xFFF4E3DD;
            case "heart": return 0xFFF4E2E2;
            case "music": return 0xFFE4ECE5;
            case "game": return 0xFFEAE8F2;
            case "book": return 0xFFF1EAD9;
            case "folder": return 0xFFF2E8D8;
            case "image": return 0xFFE4EAF2;
            case "chat": return 0xFFE7EFE9;
            case "camera": return 0xFFE3EAF1;
            case "clock": return 0xFFE8E7F1;
            case "list": return 0xFFE3EBE4;
            case "calc": return 0xFFEFE7DA;
            case "brush": return 0xFFE3EBEB;
            case "globe": return 0xFFE2ECE9;
            case "spark": return 0xFFF2E4D9;
            case "leaf": return 0xFFE5EDE2;
            case "work": return 0xFFF0E8DC;
            case "flask": return 0xFFE9E5F0;
            case "grid": return 0xFFEFE8DF;
            default: return 0xFFF0EADF;
        }
    }

    public static IconView.Type typeOf(String key) {
        if (key == null) return IconView.Type.FOLDER;
        switch (key) {
            case "home": return IconView.Type.HOME;
            case "inbox": return IconView.Type.INBOX;
            case "star": return IconView.Type.STAR;
            case "heart": return IconView.Type.HEART;
            case "music": return IconView.Type.MUSIC;
            case "game": return IconView.Type.GAME;
            case "book": return IconView.Type.BOOK;
            case "folder": return IconView.Type.FOLDER;
            case "image": return IconView.Type.IMAGE;
            case "chat": return IconView.Type.CHAT;
            case "camera": return IconView.Type.CAMERA;
            case "clock": return IconView.Type.TIMER;
            case "list": return IconView.Type.LIST;
            case "calc": return IconView.Type.CALC;
            case "brush": return IconView.Type.BRUSH;
            case "globe": return IconView.Type.NETWORK;
            case "spark": return IconView.Type.SPARK;
            case "leaf": return IconView.Type.LEAF;
            case "work": return IconView.Type.BRIEFCASE;
            case "flask": return IconView.Type.FLASK;
            case "grid": return IconView.Type.GRID;
            default: return IconView.Type.FOLDER;
        }
    }
}
