package fr.deaddiscord.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Masque les arguments sensibles des commandes avant de les afficher dans le salon admin.
 * Indispensable avec AuthMe : sans ça, « /login monMotDePasse » apparaîtrait en clair sur Discord.
 *
 * <p>Chaque entrée est {@code nom} (on garde seulement le nom de la commande) ou {@code nom=N}
 * (on garde les N premiers mots, le reste devient {@code ***}). Exemples : {@code login} →
 * « login *** » ; {@code authme=2} → « authme register *** » ; {@code msg=2} → « msg Steve *** ».</p>
 */
public final class CommandCensor {

    private final Map<String, Integer> keep;

    public CommandCensor(Map<String, Integer> keep) {
        this.keep = keep;
    }

    /** Lit des entrées {@code nom} ou {@code nom=N} (insensible à la casse). */
    public static Map<String, Integer> parse(Collection<String> entries) {
        Map<String, Integer> map = new HashMap<>();
        if (entries == null) return map;
        for (String e : entries) {
            if (e == null || e.isBlank()) continue;
            String name = e.strip();
            int n = 1;
            int eq = name.indexOf('=');
            if (eq > 0) {
                try {
                    n = Math.max(1, Integer.parseInt(name.substring(eq + 1).strip()));
                } catch (NumberFormatException ignored) {
                    n = 1;
                }
                name = name.substring(0, eq).strip();
            }
            map.put(base(name), n);
        }
        return map;
    }

    /** Nom de commande normalisé : minuscules, sans « / » ni préfixe de plugin (« authme:login » → « login »). */
    public static String base(String token) {
        String t = token == null ? "" : token.strip().toLowerCase(Locale.ROOT);
        while (t.startsWith("/")) t = t.substring(1);
        int colon = t.indexOf(':');
        return colon >= 0 ? t.substring(colon + 1) : t;
    }

    /** Retourne la commande (sans « / » initial) avec les arguments sensibles remplacés par {@code ***}. */
    public String apply(String line) {
        if (line == null) return "";
        String s = line.strip();
        while (s.startsWith("/")) s = s.substring(1).stripLeading();
        if (s.isEmpty()) return s;
        String[] tok = s.split("\\s+");
        Integer n = keep.get(base(tok[0]));
        if (n != null) return hideFrom(tok, n);
        if (base(tok[0]).equals("execute")) {
            for (int i = 1; i < tok.length - 1; i++) {
                if (tok[i].equalsIgnoreCase("run")) {
                    Integer k = keep.get(base(tok[i + 1]));
                    if (k != null) return hideFrom(tok, i + 1 + k);
                }
            }
        }
        return s;
    }

    private static String hideFrom(String[] tok, int keepCount) {
        if (tok.length <= keepCount) return String.join(" ", tok);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keepCount; i++) {
            if (i > 0) sb.append(' ');
            sb.append(tok[i]);
        }
        return sb.append(" ***").toString();
    }
}
