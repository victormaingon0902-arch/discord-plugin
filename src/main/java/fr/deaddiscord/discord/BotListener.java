package fr.deaddiscord.discord;

import fr.deaddiscord.DeadDiscord;
import fr.deaddiscord.Settings;
import fr.deaddiscord.link.LinkStore;
import fr.deaddiscord.util.Ansi;
import fr.deaddiscord.util.Texts;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/** Tout ce qui arrive de Discord : messages des salons et commandes slash. */
public final class BotListener extends ListenerAdapter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final DeadDiscord plugin;

    public BotListener(DeadDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onShutdown(ShutdownEvent event) {
        var code = event.getCloseCode();
        if (code != null && code.getCode() == 4014) {
            plugin.getLogger().severe("Discord a refusé la connexion : les « intents » ne sont pas activés. Dans le Developer Portal > Bot, "
                    + "active « MESSAGE CONTENT INTENT », enregistre puis redémarre le serveur.");
        } else if (code != null && code.getCode() == 4004) {
            plugin.getLogger().severe("Discord a refusé la connexion : token invalide. Régénère-le dans le Developer Portal > Bot > Reset Token.");
        } else {
            plugin.getLogger().info("Connexion Discord fermée" + (code == null ? "" : " (code " + code.getCode() + ")") + ".");
        }
    }

    // ================================================================== messages

    @Override
    public void onMessageReceived(MessageReceivedEvent e) {
        try {
            if (!e.isFromGuild() || e.getAuthor().isBot() || e.isWebhookMessage()) return;
            Settings s = plugin.settings();
            if (!s.guildId.isEmpty() && !e.getGuild().getId().equals(s.guildId)) return;
            String channelId = e.getChannel().getId();
            if (!s.adminChannelId.isEmpty() && channelId.equals(s.adminChannelId)) {
                handleAdminMessage(e, s);
            } else if (!s.chatChannelId.isEmpty() && channelId.equals(s.chatChannelId)) {
                handleChatMessage(e, s);
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Erreur en traitant un message Discord", ex);
        }
    }

    private void handleChatMessage(MessageReceivedEvent e, Settings s) {
        String display = e.getMessage().getContentDisplay();
        // 1) message coloré : « !c &cBonjour &aà tous »
        String colored = coloredBody(display, s);
        if (colored != null) {
            if (colored.isBlank()) return;
            String discordName = e.getMember() != null ? e.getMember().getEffectiveName() : e.getAuthor().getName();
            String header = "**" + Texts.escapeMarkdown(discordName) + "**";
            e.getChannel().sendMessage(Ansi.message(header, colored, Ansi.Base.NONE, s.colorsMaxLength))
                    .queue(null, err -> plugin.getLogger().warning("Message coloré impossible : " + err.getMessage()));
            if (s.colorsDeleteOriginal) e.getMessage().delete().queue(null, err -> { /* pas la permission « Gérer les messages » : tant pis */ });
            if (s.colorsRelayToGame) relayToGame(authorName(e, s), Ansi.strip(colored), s);
            return;
        }
        // 2) message normal -> jeu
        if (!s.discordToMc) return;
        for (String prefix : s.ignorePrefixes) {
            if (!prefix.isEmpty() && display.startsWith(prefix)) return;
        }
        StringBuilder text = new StringBuilder(display);
        for (Message.Attachment att : e.getMessage().getAttachments()) {
            if (text.length() > 0) text.append(' ');
            text.append(att.isImage() ? "[image]" : "[fichier]");
        }
        if (text.toString().isBlank()) return;
        relayToGame(authorName(e, s), text.toString(), s);
    }

    /** Si le message commence par le préfixe des couleurs (« !c »), renvoie le reste ; sinon null. */
    private static String coloredBody(String display, Settings s) {
        if (!s.colorsEnabled) return null;
        String prefix = s.colorsPrefix;
        if (display.length() <= prefix.length() || !display.regionMatches(true, 0, prefix, 0, prefix.length())) return null;
        if (!Character.isWhitespace(display.charAt(prefix.length()))) return null;
        return display.substring(prefix.length()).strip();
    }

    private String authorName(MessageReceivedEvent e, Settings s) {
        return displayName(e.getAuthor().getId(), e.getMember(), e.getAuthor().getName(), s);
    }

    /** Pseudo Minecraft si le compte est lié (et que l'option est active), sinon nom Discord. */
    private String displayName(String userId, Member member, String username, Settings s) {
        if (s.useLinkedName) {
            List<LinkStore.Link> mine = plugin.links().byDiscord(userId);
            if (!mine.isEmpty()) return mine.get(0).name();
        }
        return member != null ? member.getEffectiveName() : username;
    }

    private void relayToGame(String name, String text, Settings s) {
        String msg = Texts.truncate(text.replace("\r", "").replace('\n', ' ').strip(), s.maxIncomingLength);
        if (msg.isEmpty()) return;
        Component line = MM.deserialize(s.mcFormat,
                Placeholder.unparsed("user", name),
                Placeholder.unparsed("message", msg));
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(line));
    }

    // ------------------------------------------------------------------ salon admin (préfixe !)

    private void handleAdminMessage(MessageReceivedEvent e, Settings s) {
        if (!s.execEnabled) return;
        String raw = e.getMessage().getContentRaw().strip();
        if (!raw.startsWith(s.execPrefix)) return;
        String body = raw.substring(s.execPrefix.length()).strip();
        if (body.isEmpty()) return;

        Member m = e.getMember();
        List<String> roles = m == null ? List.of() : m.getRoles().stream().map(Role::getId).toList();
        if (!plugin.admin().authorized(roles, e.getAuthor().getId())) {
            e.getChannel().sendMessage("⛔ Tu n'es pas autorisé à utiliser les commandes du serveur.").queue(null, err -> { });
            return;
        }
        String who = e.getAuthor().getName();
        String lower = body.toLowerCase(Locale.ROOT);
        if (lower.equals("aide") || lower.equals("help-discord")) {
            e.getChannel().sendMessage(helpText(s)).queue(null, err -> { });
        } else if (lower.equals("annonce") || lower.startsWith("annonce ")) {
            String text = body.substring("annonce".length()).strip();
            if (text.isEmpty()) {
                e.getChannel().sendMessage("Usage : `" + s.execPrefix + "annonce Titre | sous-titre` (couleurs : `&c` rouge, `&a` vert, `&l` gras…)").queue(null, err -> { });
                return;
            }
            int n = plugin.announcer().announce(text, 0, who + " (Discord)");
            e.getChannel().sendMessage("📢 Annonce affichée sur l'écran de **" + n + "** joueur(s).").queue(null, err -> { });
        } else {
            plugin.admin().execute(body, who, out -> e.getChannel().sendMessage(out).queue(null, err -> { }));
        }
    }

    private static String helpText(Settings s) {
        String p = s.execPrefix;
        return "**Commandes du salon admin**\n"
                + "`" + p + "<commande>` : exécute une commande Minecraft (ex. `" + p + "list`, `" + p + "gamemode creative Steve`)\n"
                + "`" + p + "annonce Titre | sous-titre` : annonce en grand sur l'écran de tous les joueurs\n"
                + "`/cmd`, `/annonce` : mêmes commandes en slash\n"
                + "Les commandes tapées en jeu s'affichent ici automatiquement.";
    }

    // ================================================================== slash

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
        try {
            Settings s = plugin.settings();
            if (e.getGuild() == null) return;
            if (!s.guildId.isEmpty() && !e.getGuild().getId().equals(s.guildId)) return;
            switch (e.getName()) {
                case "link" -> slashLink(e, s);
                case "unlink" -> slashUnlink(e, s);
                case "couleur" -> slashColor(e, s);
                case "cmd" -> slashCmd(e, s);
                case "annonce" -> slashAnnounce(e, s);
                default -> { }
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Erreur en traitant une commande slash", ex);
            if (!e.isAcknowledged()) e.reply("Erreur interne, réessaie.").setEphemeral(true).queue(null, err -> { });
        }
    }

    private static void reply(SlashCommandInteractionEvent e, String text) {
        e.reply(Texts.truncate(text, 1900)).setEphemeral(true).queue(null, err -> { });
    }

    private static String option(SlashCommandInteractionEvent e, String name) {
        OptionMapping om = e.getOption(name);
        return om == null ? null : om.getAsString();
    }

    // ------------------------------------------------------------------ /link /unlink

    private void slashLink(SlashCommandInteractionEvent e, Settings s) {
        if (!s.linkEnabled) {
            reply(e, "La liaison de compte est désactivée.");
            return;
        }
        String code = option(e, "code");
        LinkStore.Result r = plugin.links().confirm(code, e.getUser().getId(), e.getUser().getName(), s.maxAccountsPerDiscord);
        switch (r.status()) {
            case OK -> {
                String name = r.link().name();
                reply(e, "✅ Ton compte Discord est maintenant lié à **" + Texts.escapeMarkdown(name) + "**. Ton profil en jeu l'affichera.");
                plugin.bot().setLinkedRole(e.getUser().getId(), true);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayerExact(name);
                    if (p != null) {
                        p.sendMessage(MM.deserialize("<dark_gray>[<#5865F2>Discord</#5865F2>]</dark_gray> <green>Compte lié à <white><user></white> !</green>",
                                Placeholder.unparsed("user", r.link().discordName())));
                    }
                });
                plugin.getLogger().info("Liaison : " + name + " <-> " + r.link().discordName() + " (" + r.link().discordId() + ")");
            }
            case INVALID_CODE -> reply(e, "❌ Code invalide. Tape `/link discord` en jeu pour en obtenir un (il ne sert qu'une fois).");
            case EXPIRED -> reply(e, "⌛ Ce code a expiré. Tape à nouveau `/link discord` en jeu.");
            case NAME_TAKEN -> reply(e, "❌ Ce pseudo Minecraft est déjà lié à un autre compte Discord. Il faut d'abord faire `/unlink discord` en jeu.");
            case TOO_MANY -> reply(e, "❌ Tu as déjà lié le nombre maximum de comptes Minecraft (" + s.maxAccountsPerDiscord + "). Utilise `/unlink` d'abord.");
        }
    }

    private void slashUnlink(SlashCommandInteractionEvent e, Settings s) {
        int n = plugin.links().unlinkDiscord(e.getUser().getId());
        if (n == 0) {
            reply(e, "Aucun compte Minecraft n'est lié à ton Discord.");
            return;
        }
        plugin.bot().setLinkedRole(e.getUser().getId(), false);
        reply(e, "✅ " + n + " compte(s) Minecraft délié(s).");
    }

    // ------------------------------------------------------------------ /couleur

    private void slashColor(SlashCommandInteractionEvent e, Settings s) {
        if (!s.colorsEnabled) {
            reply(e, "Les messages colorés sont désactivés.");
            return;
        }
        String text = option(e, "texte");
        if (text == null || text.isBlank()) {
            reply(e, "Écris un texte.");
            return;
        }
        String fgName = option(e, "couleur");
        String bgName = option(e, "fond");
        String styleName = option(e, "style");
        String mode = option(e, "affichage");
        int fg = fgName == null ? -1 : Ansi.fgCode(fgName);
        int bg = bgName == null ? -1 : Ansi.bgCode(bgName);
        boolean bold = styleName != null && styleName.contains("gras");
        boolean underline = styleName != null && styleName.contains("souligne");

        if ("embed".equals(mode)) {
            String body = Texts.truncate(Ansi.strip(text), 3900);
            EmbedBuilder eb = new EmbedBuilder()
                    .setDescription(bold ? "**" + body + "**" : body)
                    .setColor(Ansi.embedColor(fg));
            e.replyEmbeds(eb.build()).queue(null, err -> { });
            return;
        }
        String msg = Ansi.message("", text, new Ansi.Base(fg, bg, bold, underline), s.colorsMaxLength);
        e.reply(msg).queue(null, err -> { });
        if (s.colorsRelayToGame && s.chatChannelId.equals(e.getChannel().getId())) {
            relayToGame(displayName(e.getUser().getId(), e.getMember(), e.getUser().getName(), s), Ansi.strip(text), s);
        }
    }

    // ------------------------------------------------------------------ /cmd /annonce (admin)

    private boolean checkAdmin(SlashCommandInteractionEvent e, Settings s) {
        if (!s.execEnabled) {
            reply(e, "Les commandes depuis Discord sont désactivées.");
            return false;
        }
        if (s.adminChannelId.isEmpty() || !s.adminChannelId.equals(e.getChannel().getId())) {
            reply(e, "⛔ Cette commande ne fonctionne que dans le salon admin.");
            return false;
        }
        Member m = e.getMember();
        List<String> roles = m == null ? List.of() : m.getRoles().stream().map(Role::getId).toList();
        if (!plugin.admin().authorized(roles, e.getUser().getId())) {
            reply(e, "⛔ Tu n'es pas autorisé à utiliser cette commande.");
            return false;
        }
        return true;
    }

    private void slashCmd(SlashCommandInteractionEvent e, Settings s) {
        if (!checkAdmin(e, s)) return;
        String command = option(e, "commande");
        if (command == null || command.isBlank()) {
            reply(e, "Indique une commande.");
            return;
        }
        e.deferReply().queue(null, err -> { });
        plugin.admin().execute(command, e.getUser().getName(),
                out -> e.getHook().editOriginal(Texts.truncate(out, 2000)).queue(null, err -> { }));
    }

    private void slashAnnounce(SlashCommandInteractionEvent e, Settings s) {
        if (!checkAdmin(e, s)) return;
        String text = option(e, "texte");
        OptionMapping dur = e.getOption("duree");
        int seconds = dur == null ? 0 : dur.getAsInt();
        if (text == null || text.isBlank()) {
            reply(e, "Écris le texte de l'annonce.");
            return;
        }
        int n = plugin.announcer().announce(text, seconds, e.getUser().getName() + " (Discord)");
        e.reply("📢 Annonce affichée sur l'écran de **" + n + "** joueur(s) : " + Texts.truncate(Ansi.strip(text), 300))
                .queue(null, err -> { });
    }
}
