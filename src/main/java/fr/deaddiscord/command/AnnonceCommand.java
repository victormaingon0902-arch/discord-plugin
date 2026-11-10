package fr.deaddiscord.command;

import fr.deaddiscord.DeadDiscord;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** {@code /annonce Titre | sous-titre} : gros titre sur l'écran de tous les joueurs. */
public final class AnnonceCommand implements CommandExecutor {

    private final DeadDiscord plugin;

    public AnnonceCommand(DeadDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("deaddiscord.announce")) {
            sender.sendMessage(LinkCommand.MM.deserialize(LinkCommand.PREFIX + "<red>Tu n'as pas la permission.</red>"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(LinkCommand.MM.deserialize(LinkCommand.PREFIX
                    + "<gray>Utilisation : <white>/annonce Titre | sous-titre</white> <dark_gray>(couleurs : &c &a &l …)</dark_gray></gray>"));
            return true;
        }
        int n = plugin.announcer().announce(String.join(" ", args), 0, sender.getName());
        sender.sendMessage(LinkCommand.MM.deserialize(LinkCommand.PREFIX + "<green>Annonce envoyée à " + n + " joueur(s).</green>"));
        return true;
    }
}
