package fr.deaddiscord.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Messages colorés pour Discord.
 *
 * <p>Discord sait afficher de la couleur dans un bloc de code « ansi » (desktop et navigateur, pas encore sur
 * mobile). On y écrit des séquences ANSI : {@code ESC[0;1;31;44m} = gras + rouge + fond bleu foncé.
 * Cette classe convertit le texte « à la Minecraft » ({@code &c}, {@code &l}, {@code &n}, {@code &r}) en séquences
 * ANSI et gère l'échappement pour que personne ne puisse casser le bloc de code.</p>
 *
 * <p>Classe volontairement sans aucune dépendance (ni Bukkit ni JDA) pour pouvoir être testée seule.</p>
 */
public final class Ansi {

    public static final char ESC = '\u001b';
    /** Limite Discord : 2000 caractères, on garde une petite marge. */
    public static final int MAX_MESSAGE = 1990;

    /** Style de base appliqué à tout le texte (les codes &amp; peuvent ensuite le modifier). -1 = aucun. */
    public record Base(int fg, int bg, boolean bold, boolean underline) {
        public static final Base NONE = new Base(-1, -1, false, false);
    }

    private Ansi() {
    }

    // ------------------------------------------------------------------ noms de couleurs

    /** Couleur de texte (30-37) depuis un nom français ou anglais, -1 si inconnu. */
    public static int fgCode(String name) {
        return switch (norm(name)) {
            case "gris", "gray", "grey", "noir", "black" -> 30;
            case "rouge", "red" -> 31;
            case "vert", "green" -> 32;
            case "jaune", "yellow" -> 33;
            case "bleu", "blue" -> 34;
            case "rose", "pink", "magenta", "violet", "purple" -> 35;
            case "cyan", "turquoise", "aqua" -> 36;
            case "blanc", "white" -> 37;
            default -> -1;
        };
    }

    /** Couleur de fond (40-47) depuis un nom français ou anglais, -1 si inconnu. */
    public static int bgCode(String name) {
        return switch (norm(name)) {
            case "bleunuit", "nuit", "darkblue", "bleufonce" -> 40;
            case "orange" -> 41;
            case "bleumarbre", "marbre", "marble" -> 42;
            case "turquoise", "cyan", "aqua" -> 43;
            case "gris", "gray", "grey" -> 44;
            case "indigo", "violet", "purple" -> 45;
            case "grisclair", "lightgray", "lightgrey" -> 46;
            case "blanc", "white" -> 47;
            default -> -1;
        };
    }

    /** Couleur RVB (barre latérale d'un embed) la plus proche d'un code de texte. */
    public static int embedColor(int fg) {
        return switch (fg) {
            case 30 -> 0x4F545C;
            case 31 -> 0xED4245;
            case 32 -> 0x57F287;
            case 33 -> 0xFEE75C;
            case 34 -> 0x3B82F6;
            case 35 -> 0xEB459E;
            case 36 -> 0x2DD4BF;
            case 37 -> 0xFFFFFF;
            default -> 0x5865F2;
        };
    }

    private static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replaceAll("[\\s_\\-]", "");
    }

    // ------------------------------------------------------------------ codes &x

    /** Correspondance couleur Minecraft → couleur ANSI de Discord (8 couleurs seulement), -1 si ce n'est pas une couleur. */
    private static int mcColor(char k) {
        return switch (k) {
            case '0', '8' -> 30;
            case '1', '9' -> 34;
            case '2', 'a' -> 32;
            case '3', 'b' -> 36;
            case '4', 'c' -> 31;
            case '5', 'd' -> 35;
            case '6', 'e' -> 33;
            case '7', 'f' -> 37;
            default -> -1;
        };
    }

    private static boolean isCodeChar(char c) {
        return c == '&' || c == '§';
    }

    private static boolean isFormatKey(char k) {
        return (k >= '0' && k <= '9') || (k >= 'a' && k <= 'f') || (k >= 'k' && k <= 'o') || k == 'r';
    }

    /** Retire les codes de couleur/format ({@code &c}, {@code &l}…) et ne garde que le texte. */
    public static String strip(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length() && text.charAt(i + 1) == '&') {
                sb.append('&'); // « && » = un & littéral
                i++;
                continue;
            }
            if (isCodeChar(c) && i + 1 < text.length() && isFormatKey(Character.toLowerCase(text.charAt(i + 1)))) {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Neutralise ce qui pourrait casser le bloc de code ou injecter de l'ANSI : caractères de contrôle supprimés
     * (dont ESC) et accents graves remplacés par un caractère quasi identique (impossible d'écrire « ``` »).
     */
    public static String sanitize(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == 0x7f) continue;
            if (c < 0x20 && c != '\n' && c != '\t') continue;
            sb.append(c == '`' ? 'ˋ' : c);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ rendu

    private static int key(int fg, int bg, boolean bold, boolean ul) {
        return (fg + 1) | ((bg + 1) << 8) | (bold ? 1 << 16 : 0) | (ul ? 1 << 17 : 0);
    }

    private static void emit(StringBuilder out, int fg, int bg, boolean bold, boolean ul) {
        out.append(ESC).append("[0");
        if (bold) out.append(";1");
        if (ul) out.append(";4");
        if (fg >= 0) out.append(';').append(fg);
        if (bg >= 0) out.append(';').append(bg);
        out.append('m');
    }

    /**
     * Convertit le texte en séquences ANSI (sans les ```), limité à {@code maxVisible} caractères visibles.
     * Une séquence n'est émise que juste avant un caractère visible et seulement si le style change.
     */
    public static String render(String text, Base base, int maxVisible) {
        String s = sanitize(text);
        Base b = base == null ? Base.NONE : base;
        StringBuilder out = new StringBuilder(s.length() + 32);
        int fg = b.fg(), bg = b.bg();
        boolean bold = b.bold(), ul = b.underline();
        int last = 0;
        int visible = 0;
        boolean cut = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length() && s.charAt(i + 1) == '&') {
                i++; // « && » = un & littéral : on garde le premier & et on saute le second
            } else if (isCodeChar(c) && i + 1 < s.length()) {
                char k = Character.toLowerCase(s.charAt(i + 1));
                int col = mcColor(k);
                if (col >= 0) {
                    // comme en jeu : une couleur annule gras/souligné
                    fg = col;
                    bold = false;
                    ul = false;
                    i++;
                    continue;
                }
                if (k == 'l') { bold = true; i++; continue; }
                if (k == 'n') { ul = true; i++; continue; }
                if (k == 'r') { fg = -1; bg = -1; bold = false; ul = false; i++; continue; }
                if (k == 'o' || k == 'm' || k == 'k') { i++; continue; } // italique/barré/obfusqué : non supportés
            }
            if (visible >= maxVisible) {
                cut = true;
                break;
            }
            int key = key(fg, bg, bold, ul);
            if (key != last) {
                emit(out, fg, bg, bold, ul);
                last = key;
            }
            out.append(c);
            visible++;
        }
        if (cut) out.append('…');
        if (last != 0) out.append(ESC).append("[0m");
        return out.toString();
    }

    /**
     * Message Discord complet : une éventuelle ligne d'en-tête puis le bloc {@code ```ansi}.
     * Réduit automatiquement le texte pour rester sous la limite de 2000 caractères de Discord.
     */
    public static String message(String header, String text, Base base, int maxVisible) {
        String head = header == null || header.isEmpty() ? "" : header + "\n";
        int limit = Math.max(1, Math.min(maxVisible, 1900));
        while (limit > 10) {
            String msg = head + "```ansi\n" + render(text, base, limit) + "\n```";
            if (msg.length() <= MAX_MESSAGE) return msg;
            limit = limit * 3 / 4;
        }
        String plain = strip(sanitize(text));
        if (plain.length() > 1500) plain = plain.substring(0, 1500) + "…";
        String msg = head + "```\n" + plain + "\n```";
        return msg.length() <= MAX_MESSAGE ? msg : msg.substring(0, MAX_MESSAGE);
    }
}
