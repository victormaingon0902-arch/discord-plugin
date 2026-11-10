package fr.deaddiscord.link;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Liaisons pseudo Minecraft ↔ compte Discord + codes de liaison temporaires.
 *
 * <p>Les liaisons sont indexées par le <b>pseudo</b> en minuscules (et non par l'UUID) : le serveur est ouvert aux
 * comptes crackés, donc le même joueur peut avoir un UUID « premium » ou « offline » selon la façon dont il se
 * connecte. Fichier texte simple {@code links.tsv} (une ligne par liaison, colonnes séparées par des tabulations).</p>
 *
 * <p>Aucune dépendance Bukkit/JDA : testable seule.</p>
 */
public final class LinkStore {

    public record Link(String name, String discordId, String discordName, long linkedAt) {
    }

    public enum Status { OK, INVALID_CODE, EXPIRED, NAME_TAKEN, TOO_MANY }

    public record Result(Status status, Link link) {
    }

    private record Pending(String name, long expires) {
    }

    public static final int CODE_LENGTH = 6;
    // pas de 0/O ni 1/I/L : pas de confusion à la lecture
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final Path file;
    private final LongSupplier clock;
    private final Consumer<Exception> onError;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Link> byName = new ConcurrentHashMap<>();
    private final Map<String, Pending> codes = new HashMap<>();

    public LinkStore(Path file, LongSupplier clock, Consumer<Exception> onError) {
        this.file = file;
        this.clock = clock;
        this.onError = onError == null ? e -> { } : onError;
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').strip();
    }

    // ------------------------------------------------------------------ persistance

    public synchronized void load() throws IOException {
        byName.clear();
        if (!Files.exists(file)) return;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] p = line.split("\t", -1);
            if (p.length < 4 || p[0].isBlank() || p[1].isBlank()) continue;
            long at;
            try {
                at = Long.parseLong(p[3].strip());
            } catch (NumberFormatException ex) {
                at = 0L;
            }
            byName.put(key(p[0]), new Link(p[0].strip(), p[1].strip(), p[2], at));
        }
    }

    private synchronized void save() {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            List<String> lines = new ArrayList<>();
            lines.add("# DeadDiscord - liaisons : pseudo<TAB>id Discord<TAB>nom Discord<TAB>date (ms). Ne pas modifier pendant que le serveur tourne.");
            byName.values().stream()
                    .sorted(Comparator.comparing(l -> key(l.name())))
                    .forEach(l -> lines.add(clean(l.name()) + "\t" + clean(l.discordId()) + "\t" + clean(l.discordName()) + "\t" + l.linkedAt()));
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            onError.accept(ex);
        }
    }

    // ------------------------------------------------------------------ codes

    private void purgeExpired() {
        long now = clock.getAsLong();
        codes.values().removeIf(p -> p.expires() < now);
    }

    /** Génère un code à usage unique pour ce pseudo (remplace le précédent code de ce pseudo). */
    public synchronized String createCode(String name, long ttlMillis) {
        purgeExpired();
        codes.values().removeIf(p -> p.name().equalsIgnoreCase(name));
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            code = sb.toString();
        } while (codes.containsKey(code));
        codes.put(code, new Pending(name, clock.getAsLong() + ttlMillis));
        return code;
    }

    public static String normalizeCode(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toUpperCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) sb.append(c);
        }
        return sb.toString();
    }

    /** Valide un code saisi sur Discord. {@code maxPerDiscord} ≤ 0 = illimité. */
    public synchronized Result confirm(String rawCode, String discordId, String discordName, int maxPerDiscord) {
        String code = normalizeCode(rawCode);
        Pending p = codes.get(code);
        if (p == null) return new Result(Status.INVALID_CODE, null);
        long now = clock.getAsLong();
        if (p.expires() < now) {
            codes.remove(code);
            return new Result(Status.EXPIRED, null);
        }
        Link existing = byName.get(key(p.name()));
        if (existing != null && !existing.discordId().equals(discordId)) {
            return new Result(Status.NAME_TAKEN, existing);
        }
        if (existing == null && maxPerDiscord > 0 && byDiscord(discordId).size() >= maxPerDiscord) {
            return new Result(Status.TOO_MANY, null);
        }
        codes.remove(code);
        Link link = new Link(p.name(), discordId, clean(discordName), now);
        byName.put(key(p.name()), link);
        save();
        return new Result(Status.OK, link);
    }

    // ------------------------------------------------------------------ lecture / suppression

    public Link byName(String name) {
        return byName.get(key(name));
    }

    public List<Link> byDiscord(String discordId) {
        List<Link> out = new ArrayList<>();
        for (Link l : byName.values()) if (l.discordId().equals(discordId)) out.add(l);
        out.sort(Comparator.comparing(l -> key(l.name())));
        return out;
    }

    public int size() {
        return byName.size();
    }

    /** Supprime la liaison d'un pseudo ; retourne l'ancienne liaison ou null. */
    public synchronized Link unlink(String name) {
        Link old = byName.remove(key(name));
        if (old != null) save();
        return old;
    }

    /** Supprime toutes les liaisons d'un compte Discord ; retourne le nombre supprimé. */
    public synchronized int unlinkDiscord(String discordId) {
        List<Link> mine = byDiscord(discordId);
        for (Link l : mine) byName.remove(key(l.name()));
        if (!mine.isEmpty()) save();
        return mine.size();
    }
}
