package fr.deaddiscord.util;

import java.util.ArrayList;
import java.util.List;

/** Petits utilitaires de texte (aucune dépendance Bukkit/JDA). */
public final class Texts {

    private Texts() {
    }

    /** Casse les mentions (@everyone, @here, &lt;@id&gt;…) en glissant un espace de largeur nulle après chaque @. */
    public static String neutralizeMentions(String s) {
        return s == null ? "" : s.replace("@", "@​");
    }

    /** Échappe le Markdown de Discord (pour les pseudos : Dead_Player_1 ne doit pas passer en italique). */
    public static String escapeMarkdown(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '*' || c == '_' || c == '~' || c == '`' || c == '|') sb.append('\\');
            sb.append(c);
        }
        return sb.toString();
    }

    /** Coupe à {@code max} caractères (avec « … » si coupé). */
    public static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        if (max <= 1) return s.substring(0, Math.max(0, max));
        return s.substring(0, max - 1) + "…";
    }

    /** Texte issu du jeu ou de Discord, prêt à être envoyé tel quel : sans mention, sans retour chariot, tronqué. */
    public static String cleanChat(String s, int max) {
        if (s == null) return "";
        return truncate(neutralizeMentions(s.replace("\r", "")).strip(), max);
    }

    /** Pour mettre du texte dans un `code en ligne` : on remplace les accents graves. */
    public static String inlineCode(String s) {
        return s == null ? "" : s.replace('`', '\'');
    }

    /** Regroupe des lignes en morceaux de {@code max} caractères au plus (une ligne trop longue est découpée). */
    public static List<String> chunk(List<String> lines, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String raw : lines) {
            String line = raw;
            while (line.length() > max) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                out.add(line.substring(0, max));
                line = line.substring(max);
            }
            if (cur.length() + line.length() + 1 > max && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append('\n');
            cur.append(line);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
