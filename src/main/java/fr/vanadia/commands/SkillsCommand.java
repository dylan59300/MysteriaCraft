package fr.vanadia.commands;

import fr.vanadia.Vanadia;
import fr.vanadia.player.PlayerData;
import fr.vanadia.skills.Skill;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class SkillsCommand implements CommandExecutor, TabCompleter {

    private final Vanadia plugin;

    public SkillsCommand(Vanadia plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getClassType() == null) {
            plugin.getMessageUtil().send(player, "class-no-class");
            return true;
        }

        if (args.length == 0) {
            showSkills(player, data);
            return true;
        }

        String skillId = args[0].toLowerCase();
        plugin.getSkillManager().useSkill(player, skillId);
        return true;
    }

    private void showSkills(Player player, PlayerData data) {
        plugin.getMessageUtil().send(player, "skill-list-header");
        List<Skill> skills = plugin.getSkillManager().getSkillsForClass(data.getClassType());

        for (Skill skill : skills) {
            String status;
            if (data.getLevel() < skill.getRequiredLevel()) {
                status = "&c[Niv." + skill.getRequiredLevel() + "]";
            } else if (data.isSkillOnCooldown(skill.getId())) {
                long cd = data.getSkillCooldownRemaining(skill.getId()) / 1000;
                status = "&e[CD: " + cd + "s]";
            } else {
                status = "&a[Pret]";
            }

            String mana = skill.getManaCost() > 0 ? " &9[" + skill.getManaCost() + " mana]" : "";
            plugin.getMessageUtil().sendRaw(player,
                    " &7- &e" + skill.getName() + " " + status + mana +
                            "\n   &7" + skill.getDescription());
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length != 1) return List.of();

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getClassType() == null) return List.of();

        return plugin.getSkillManager().getAvailableSkills(data).stream()
                .map(Skill::getId)
                .filter(id -> id.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
    }
}
