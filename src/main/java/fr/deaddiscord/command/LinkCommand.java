package fr.deaddiscord.command;

import fr.deaddiscord.DeadDiscord;
import fr.deaddiscord.Settings;
import fr.deaddiscord.link.LinkStore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** {@code /link discord} : génère un code à saisir sur Discord avec {@code /link code:XXXXXX}. */
public final class LinkCommand implements TabExecutor {

    static final MiniMessage MM = MiniMessage.miniMessage();
    static final String PREFIX = "<dark_gray>[<#5865F2>Discord</#5865F2>]</dark_gray> ";

    private final DeadDiscord plugin;

    public LinkCommand(DeadDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        Settings s = plugin.settings();
        if (!s.linkEnabled) {
            p.sendMessage(MM.deserialize(PREFIX + "<red>La liaison de compte est désactivée.</red>"));
            return true;
        }
        if (args.length > 0 && !args[0].equalsIgnoreCase("discord")) {
            p.sendMessage(MM.deserialize(PREFIX + "<gray>Utilisation : <white>/link discord</white></gray>"));
            return true;
        }
        if (!p.hasPermission("deaddiscord.link")) {
            p.sendMessage(MM.deserialize(PREFIX + "<red>Tu n'as pas la permission.</red>"));
            return true;
        }
        if (!plugin.bot().isReady()) {
            p.sendMessage(MM.deserialize(PREFIX + "<red>Le bot Discord n'est pas connecté pour le moment, réessaie dans un instant.</red>"));
            return true;
        }
        LinkStore.Link existing = plugin.links().byName(p.getName());
        if (existing != null) {
            p.sendMessage(MM.deserialize(PREFIX + "<gray>Ton compte est déjà lié à <white><user></white>. Pour le délier : <white>/unlink discord</white></gray>",
                    Placeholder.unparsed("user", existing.discordName().isBlank() ? existing.discordId() : existing.discordName())));
            return true;
        }
        String code = plugin.links().createCode(p.getName(), s.linkCodeMinutes * 60_000L);
        // le code ne contient que A-Z / 2-9 : on peut l'insérer tel quel dans la balise
        p.sendMessage(MM.deserialize(PREFIX + "<gray>Pour lier ton compte, ouvre le Discord du serveur et tape :</gray>"));
        p.sendMessage(MM.deserialize("  <click:copy_to_clipboard:" + code + "><hover:show_text:'<gray>Clique pour copier le code'>"
                + "<white>/link code:</white><yellow><bold>" + code + "</bold></yellow></hover></click>"));
        p.sendMessage(MM.deserialize("<gray>Le code est valable <white>" + s.linkCodeMinutes + " minutes</white> et ne sert qu'une fois.</gray>"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "discord".startsWith(args[0].toLowerCase(Locale.ROOT))) return List.of("discord");
        return List.of();
    }
}
