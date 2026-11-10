package fr.deaddiscord.discord;

import fr.deaddiscord.DeadDiscord;
import fr.deaddiscord.Settings;
import fr.deaddiscord.util.Texts;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import org.bukkit.Bukkit;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Connexion à Discord (JDA) et envoi des messages vers les salons.
 * Tout se fait en asynchrone : rien ici ne doit bloquer le thread principal du serveur
 * (sauf {@link #stop()} qui attend très brièvement l'envoi du dernier message à l'arrêt).
 */
public final class DiscordBot {

    private final DeadDiscord plugin;
    private volatile JDA jda;
    private volatile boolean ready;
    private final AtomicLong lastWarn = new AtomicLong();

    public DiscordBot(DeadDiscord plugin) {
        this.plugin = plugin;
    }

    public boolean isReady() {
        return ready && jda != null;
    }

    public JDA jda() {
        return jda;
    }

    // ------------------------------------------------------------------ cycle de vie

    public void start() {
        Settings s = plugin.settings();
        if (s.token.isEmpty()) {
            plugin.getLogger().warning("Aucun token Discord : renseigne discord.token dans config.yml (ou la variable DEADDISCORD_TOKEN) puis redémarre.");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Jamais de ping @everyone / @here / rôles / utilisateurs depuis le bot, quoi qu'il arrive.
                MessageRequest.setDefaultMentions(EnumSet.noneOf(Message.MentionType.class));
                JDA j = JDABuilder.createLight(s.token, EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT))
                        .setMemberCachePolicy(MemberCachePolicy.NONE)
                        .addEventListeners(new BotListener(plugin))
                        .build();
                jda = j;
                j.awaitReady();
                onReady(j);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Connexion à Discord impossible (token invalide ? intents non activés ?) : " + ex.getMessage());
            }
        });
    }

    private void onReady(JDA j) {
        Settings s = plugin.settings();
        Guild guild = s.guildId.isEmpty() ? null : j.getGuildById(s.guildId);
        if (guild == null) {
            plugin.getLogger().warning("Serveur Discord introuvable : vérifie discord.guild-id et que le bot a bien été invité sur ce serveur.");
        } else {
            registerCommands(guild);
        }
        if (!s.chatChannelId.isEmpty() && j.getTextChannelById(s.chatChannelId) == null) {
            plugin.getLogger().warning("Salon de chat introuvable (channels.chat) ou le bot n'y a pas accès.");
        }
        if (!s.adminChannelId.isEmpty() && j.getTextChannelById(s.adminChannelId) == null) {
            plugin.getLogger().warning("Salon admin introuvable (channels.admin) ou le bot n'y a pas accès.");
        }
        ready = true;
        plugin.getLogger().info("Bot Discord connecté : " + j.getSelfUser().getName());
        if (s.evStartStop) sendChatLine(s.fmtStart);
        Bukkit.getScheduler().runTask(plugin, this::updatePresence);
    }

    public void stop() {
        JDA j = jda;
        if (j == null) return;
        Settings s = plugin.settings();
        try {
            if (ready && s.evStartStop && !s.chatChannelId.isEmpty()) {
                TextChannel ch = j.getTextChannelById(s.chatChannelId);
                if (ch != null) ch.sendMessage(s.fmtStop).submit().get(3, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {
            // on ne bloque pas l'arrêt du serveur pour un message
        }
        ready = false;
        j.shutdown();
        try {
            if (!j.awaitShutdown(5, TimeUnit.SECONDS)) j.shutdownNow();
        } catch (InterruptedException ex) {
            j.shutdownNow();
            Thread.currentThread().interrupt();
        }
        jda = null;
    }

    // ------------------------------------------------------------------ commandes slash

    private void registerCommands(Guild guild) {
        Settings s = plugin.settings();
        // Commandes d'administration : invisibles pour tout le monde sauf les administrateurs Discord.
        // Le plugin vérifie en plus les rôles/utilisateurs autorisés (config) et le salon admin.
        DefaultMemberPermissions adminOnly = DefaultMemberPermissions.DISABLED;

        OptionData fg = new OptionData(OptionType.STRING, "couleur", "Couleur du texte", false)
                .addChoice("Gris", "gris").addChoice("Rouge", "rouge").addChoice("Vert", "vert").addChoice("Jaune", "jaune")
                .addChoice("Bleu", "bleu").addChoice("Rose", "rose").addChoice("Cyan", "cyan").addChoice("Blanc", "blanc");
        OptionData bg = new OptionData(OptionType.STRING, "fond", "Couleur de fond (affichage ansi)", false)
                .addChoice("Bleu nuit", "bleunuit").addChoice("Orange", "orange").addChoice("Bleu marbre", "bleumarbre")
                .addChoice("Turquoise", "turquoise").addChoice("Gris", "gris").addChoice("Indigo", "indigo")
                .addChoice("Gris clair", "grisclair").addChoice("Blanc", "blanc");
        OptionData style = new OptionData(OptionType.STRING, "style", "Style du texte (affichage ansi)", false)
                .addChoice("Gras", "gras").addChoice("Souligné", "souligne").addChoice("Gras + souligné", "gras_souligne");
        OptionData mode = new OptionData(OptionType.STRING, "affichage", "ansi = texte coloré (PC) / embed = barre colorée (partout)", false)
                .addChoice("Texte coloré (PC / navigateur)", "ansi").addChoice("Embed (compatible mobile)", "embed");

        guild.updateCommands().addCommands(
                Commands.slash("link", "Lier ton compte Discord à ton compte Minecraft")
                        .addOption(OptionType.STRING, "code", "Le code obtenu avec /link discord en jeu", true),
                Commands.slash("unlink", "Délier ton compte Discord de ton compte Minecraft"),
                Commands.slash("couleur", "Écrire un message en couleur")
                        .addOption(OptionType.STRING, "texte", "Le message (tu peux utiliser &c rouge, &a vert, &l gras, &n souligné, &r reset…)", true)
                        .addOptions(fg, bg, style, mode),
                Commands.slash("cmd", "Exécuter une commande sur le serveur Minecraft")
                        .addOption(OptionType.STRING, "commande", "La commande, sans le /", true)
                        .setDefaultPermissions(adminOnly),
                Commands.slash("annonce", "Afficher une annonce en grand sur l'écran de tous les joueurs")
                        .addOption(OptionType.STRING, "texte", "Titre | sous-titre (couleurs avec &c, &a, &l…)", true)
                        .addOptions(new OptionData(OptionType.INTEGER, "duree", "Durée d'affichage en secondes", false).setRequiredRange(1, 60))
                        .setDefaultPermissions(adminOnly)
        ).queue(ok -> plugin.getLogger().info("Commandes slash enregistrées (" + ok.size() + ")."),
                err -> plugin.getLogger().warning("Enregistrement des commandes slash impossible : " + err.getMessage()
                        + " (le bot a-t-il été invité avec le scope applications.commands ?)"));
    }

    // ------------------------------------------------------------------ envoi

    private TextChannel channel(String id) {
        JDA j = jda;
        if (j == null || !ready || id == null || id.isEmpty()) return null;
        return j.getTextChannelById(id);
    }

    private void logFailure(Throwable t) {
        long now = System.currentTimeMillis();
        long last = lastWarn.get();
        if (now - last > 60_000L && lastWarn.compareAndSet(last, now)) {
            plugin.getLogger().warning("Envoi Discord impossible : " + t.getMessage());
        }
    }

    /** Message du jeu dans le salon de chat, selon le style choisi (texte ou embed). */
    public void sendChat(String player, String message) {
        Settings s = plugin.settings();
        TextChannel ch = channel(s.chatChannelId);
        if (ch == null || !s.mcToDiscord) return;
        String msg = Texts.cleanChat(message, 1800);
        if (msg.isEmpty()) return;
        if (s.embedStyle()) {
            String avatar = s.avatarUrl.replace("{player}", URLEncoder.encode(player, StandardCharsets.UTF_8));
            EmbedBuilder eb = new EmbedBuilder().setAuthor(player, null, avatar).setDescription(msg).setColor(s.embedColor);
            ch.sendMessageEmbeds(eb.build()).queue(null, this::logFailure);
        } else {
            String text = s.chatFormat.replace("{player}", Texts.escapeMarkdown(player)).replace("{message}", msg);
            ch.sendMessage(Texts.truncate(text, 2000)).queue(null, this::logFailure);
        }
    }

    /** Ligne simple dans le salon de chat (événements, annonces…). */
    public void sendChatLine(String text) {
        Settings s = plugin.settings();
        TextChannel ch = channel(s.chatChannelId);
        if (ch == null || text == null || text.isBlank()) return;
        ch.sendMessage(Texts.truncate(text, 2000)).queue(null, this::logFailure);
    }

    /** Événement (connexion, mort, succès…) : texte simple ou petit embed coloré selon le style. */
    public void sendEvent(String text, int color) {
        Settings s = plugin.settings();
        TextChannel ch = channel(s.chatChannelId);
        if (ch == null || text == null || text.isBlank()) return;
        if (s.embedStyle()) {
            ch.sendMessageEmbeds(new EmbedBuilder().setDescription(Texts.truncate(text, 2000)).setColor(color).build())
                    .queue(null, this::logFailure);
        } else {
            ch.sendMessage(Texts.truncate(text, 2000)).queue(null, this::logFailure);
        }
    }

    /** Texte dans le salon admin. {@code sync} = on attend l'envoi (utilisé uniquement à l'arrêt du serveur). */
    public void sendAdmin(String text, boolean sync) {
        Settings s = plugin.settings();
        TextChannel ch = channel(s.adminChannelId);
        if (ch == null || text == null || text.isBlank()) return;
        MessageCreateAction action = ch.sendMessage(Texts.truncate(text, 2000));
        if (sync) {
            try {
                action.submit().get(3, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // arrêt du serveur : on n'insiste pas
            }
        } else {
            action.queue(null, this::logFailure);
        }
    }

    // ------------------------------------------------------------------ présence / rôle

    public void updatePresence() {
        Settings s = plugin.settings();
        JDA j = jda;
        if (j == null || !ready || !s.presenceEnabled) return;
        String text = Texts.truncate(s.presenceText
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers())), 120);
        Activity activity = switch (s.presenceType) {
            case "playing" -> Activity.playing(text);
            case "listening" -> Activity.listening(text);
            case "competing" -> Activity.competing(text);
            default -> Activity.watching(text);
        };
        j.getPresence().setActivity(activity);
    }

    /** Donne ou retire le rôle « compte lié » (si configuré). */
    public void setLinkedRole(String discordId, boolean grant) {
        Settings s = plugin.settings();
        JDA j = jda;
        if (j == null || !ready || s.linkedRoleId.isEmpty() || s.guildId.isEmpty()) return;
        Guild g = j.getGuildById(s.guildId);
        if (g == null) return;
        Role role = g.getRoleById(s.linkedRoleId);
        if (role == null) {
            plugin.getLogger().warning("link.linked-role-id : rôle introuvable.");
            return;
        }
        UserSnowflake user = UserSnowflake.fromId(discordId);
        if (grant) g.addRoleToMember(user, role).queue(null, this::logFailure);
        else g.removeRoleFromMember(user, role).queue(null, this::logFailure);
    }

    public String statusLine() {
        JDA j = jda;
        if (j == null) return "non connecté";
        return (ready ? "connecté" : "connexion en cours") + " (" + j.getStatus() + ", ping " + j.getGatewayPing() + " ms)";
    }

    public boolean channelOk(String id) {
        return channel(id) != null;
    }
}
