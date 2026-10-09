package com.rovtech.joyride;

/** A selectable rider and their outfit. Every attribute is an index into an option list. */
public class Rider {

    public static final int[] COLORS = {
            0xFF1C1C1E, 0xFF3A3F47, 0xFFF2F2F2, 0xFFB0B7BF, 0xFFD62828, 0xFFF77F00, 0xFFFCBF49,
            0xFFD4FF00, 0xFF7BC950, 0xFF5B6B3A, 0xFF14A098, 0xFF4CC9F0, 0xFF2B59C3, 0xFF14213D,
            0xFF7B2CBF, 0xFFFF4D8D, 0xFF7F5539, 0xFFC8A27A, 0xFFE0B526
    };
    public static final String[] COLOR_NAMES = {
            "Black", "Charcoal", "White", "Silver", "Red", "Orange", "Yellow",
            "Hi-vis", "Lime", "Olive", "Teal", "Sky", "Royal blue", "Navy",
            "Purple", "Pink", "Brown", "Tan", "Gold"
    };
    public static final int[] SKINS = {0xFFF1C27D, 0xFFE0AC69, 0xFFC68642, 0xFF8D5524, 0xFF5C3A1E};
    public static final String[] SKIN_NAMES = {"Fair", "Light", "Medium", "Brown", "Deep"};
    public static final String[] HELMETS = {"Full-face", "Adventure peak", "Retro open-face"};
    public static final String[] PATTERNS = {"Plain", "Racing stripes", "Camo", "Chevrons", "Flames", "Hi-vis bands"};
    public static final String[] ACCESSORIES = {"None", "Backpack", "Scarf", "Cape", "Bottle pack", "Panniers"};
    public static final String[] BIKE_PAINTS = {"Kaza Brown", "Himalayan Salt", "Hanle Black", "Kamet White", "Slate Poppy Blue"};
    /** tank colour, accent colour, frame colour */
    public static final int[][] BIKE_COLORS = {
            {0xFF8B5A2B, 0xFFE3CFAE, 0xFF2A2A2A},
            {0xFFB8C0C2, 0xFF2F5D8A, 0xFF2A2A2A},
            {0xFF1E1E1E, 0xFFC9A55C, 0xFF111111},
            {0xFFEDEDED, 0xFFD62828, 0xFF2A2A2A},
            {0xFF2F6DB5, 0xFFF2F2F2, 0xFF2A2A2A},
    };

    // attribute slots
    public static final int HELMET = 0, HELMET_COL = 1, STRIPE = 2, VISOR = 3, JACKET = 4, ACCENT = 5,
            PATTERN = 6, PANTS = 7, BOOTS = 8, GLOVES = 9, SKIN = 10, ACCESSORY = 11, ACC_COL = 12, BIKE = 13;
    public static final int SLOTS = 14;
    public static final String[] SLOT_NAMES = {
            "Helmet style", "Helmet colour", "Helmet stripe", "Visor tint", "Jacket", "Jacket accent",
            "Jacket pattern", "Riding pants", "Boots", "Gloves", "Skin tone", "Accessory", "Accessory colour", "Bike paint"
    };

    public static int optionCount(int slot) {
        switch (slot) {
            case HELMET: return HELMETS.length;
            case PATTERN: return PATTERNS.length;
            case SKIN: return SKINS.length;
            case ACCESSORY: return ACCESSORIES.length;
            case BIKE: return BIKE_PAINTS.length;
            default: return COLORS.length;
        }
    }

    public static String optionName(int slot, int v) {
        switch (slot) {
            case HELMET: return HELMETS[v];
            case PATTERN: return PATTERNS[v];
            case SKIN: return SKIN_NAMES[v];
            case ACCESSORY: return ACCESSORIES[v];
            case BIKE: return BIKE_PAINTS[v];
            default: return COLOR_NAMES[v];
        }
    }

    public static boolean isColorSlot(int slot) {
        return slot != HELMET && slot != PATTERN && slot != ACCESSORY && slot != BIKE;
    }

    public static int swatch(int slot, int v) {
        if (slot == SKIN) return SKINS[v];
        if (slot == BIKE) return BIKE_COLORS[v][0];
        return COLORS[v];
    }

    // ---- roster ----
    public static final String[] NAMES = {
            "Gokul", "Yaseen", "Omar", "Satya", "Bottlesage", "Pradeep", "Sanjey", "Kaushik", "Hugo", "Custom"
    };
    public static final String[] TAGLINES = {
            "Trail boss. Never takes the easy line.",
            "Desert ghost in the mountain mist.",
            "Track-day hero on public roads.",
            "Old-school soul, leather and goggles.",
            "Hydrated. Enlightened. Unstoppable.",
            "Long-haul tourer, packed for anything.",
            "Site-safety certified at 150 km/h.",
            "Lights the road on fire.",
            "Not every hero wears a helmet visor.",
            "Your rider, your rules."
    };
    //                       HEL HCL STR VIS JKT ACC PAT PNT BOT GLV SKN ACS ACC BIK
    private static final int[][] DEFAULTS = {
            {1, 5, 0, 0, 0, 5, 3, 1, 0, 5, 2, 1, 1, 0},   // Gokul
            {0, 0, 8, 8, 9, 17, 2, 9, 16, 0, 2, 2, 17, 2}, // Yaseen
            {0, 2, 4, 0, 4, 2, 1, 0, 2, 4, 1, 0, 0, 3},   // Omar
            {2, 17, 16, 0, 16, 17, 0, 13, 16, 16, 3, 2, 6, 0}, // Satya
            {1, 14, 18, 11, 10, 14, 1, 1, 0, 18, 2, 4, 11, 1}, // Bottlesage
            {0, 12, 2, 11, 12, 2, 1, 13, 0, 2, 3, 5, 0, 4}, // Pradeep
            {1, 6, 0, 5, 7, 3, 5, 13, 16, 0, 2, 1, 5, 1},  // Sanjey
            {0, 0, 4, 4, 0, 5, 4, 0, 4, 0, 2, 0, 0, 2},   // Kaushik
            {2, 3, 12, 11, 13, 4, 0, 13, 4, 4, 0, 3, 4, 3}, // Hugo
            {0, 15, 2, 0, 11, 15, 3, 1, 0, 15, 1, 0, 0, 4}, // Custom
    };

    public final int index;
    public String name;
    public final int[] v = new int[SLOTS];

    public Rider(int index) {
        this.index = index;
        this.name = NAMES[index];
        resetStyle();
    }

    public void resetStyle() {
        System.arraycopy(DEFAULTS[index], 0, v, 0, SLOTS);
    }

    public String tagline() { return TAGLINES[index]; }
    public boolean isCustom() { return index == NAMES.length - 1; }

    public int col(int slot) { return swatch(slot, v[slot]); }
    public int[] bike() { return BIKE_COLORS[v[BIKE]]; }

    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SLOTS; i++) { if (i > 0) sb.append(','); sb.append(v[i]); }
        return sb.toString();
    }

    public void decode(String s) {
        if (s == null) return;
        String[] parts = s.split(",");
        if (parts.length != SLOTS) return;
        try {
            for (int i = 0; i < SLOTS; i++) {
                int x = Integer.parseInt(parts[i]);
                if (x >= 0 && x < optionCount(i)) v[i] = x;
            }
        } catch (NumberFormatException ignored) {}
    }
}
