package com.mysteriacraft.economy.commands;

import com.mysteriacraft.core.config.ConfigManager;
import com.mysteriacraft.core.config.MessageManager;
import com.mysteriacraft.economy.EconomyManager;
import com.mysteriacraft.economy.PendingPayment;
import com.mysteriacraft.economy.PendingPaymentManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PayCommand implements CommandExecutor {

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private final MessageManager messages;
    private final ConfigManager config;
    private final PendingPaymentManager pendingPayments;

    /** Horodatage (millis) du dernier /pay reussi par joueur, pour l'anti-spam. */
    private final Map<UUID, Long> lastPayMillis = new ConcurrentHashMap<>();

    public PayCommand(Plugin plugin, EconomyManager economyManager, MessageManager messages,
                       ConfigManager config, PendingPaymentManager pendingPayments) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.messages = messages;
        this.config = config;
        this.pendingPayments = pendingPayments;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player payer)) {
            messages.send(sender, "general.commande-joueur-uniquement");
            return true;
        }

        if (args.length < 2) {
            messages.send(sender, "economie.pay-usage");
            return true;
        }

        String targetName = args[0];
        double amount;
        try {
            amount = Double.parseDouble(args[1].replace(',', '.'));
        } catch (NumberFormatException e) {
            messages.send(sender, "economie.pay-montant-invalide");
            return true;
        }

        if (amount <= 0) {
            messages.send(sender, "economie.pay-montant-invalide");
            return true;
        }

        double minimum = config.get().getDouble("economie.pay-montant-minimum", 0.0);
        if (amount < minimum) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("minimum", economyManager.format(minimum));
            messages.send(sender, "economie.pay-montant-trop-petit", placeholders);
            return true;
        }

        if (targetName.equalsIgnoreCase(payer.getName())) {
            messages.send(sender, "economie.pay-soi-meme");
            return true;
        }

        long cooldownSeconds = config.get().getLong("economie.pay-cooldown-secondes", 0);
        Long last = lastPayMillis.get(payer.getUniqueId());
        if (cooldownSeconds > 0 && last != null) {
            long remainingMillis = (last + cooldownSeconds * 1000L) - System.currentTimeMillis();
            if (remainingMillis > 0) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("secondes", String.valueOf((remainingMillis + 999) / 1000));
                messages.send(sender, "economie.pay-cooldown", placeholders);
                return true;
            }
        }

        final double finalAmount = amount;
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null) {
            handlePaymentRequest(payer, onlineTarget.getUniqueId(), onlineTarget.getName(), finalAmount);
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID targetUuid = economyManager.findUuidByName(targetName);
            if (targetUuid == null) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, "general.joueur-introuvable"));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> handlePaymentRequest(payer, targetUuid, targetName, finalAmount));
        });
        return true;
    }

    /** Verifie les fonds, puis demande confirmation si au-dessus du seuil, ou execute directement sinon. */
    private void handlePaymentRequest(Player payer, UUID targetUuid, String targetName, double amount) {
        if (!economyManager.has(payer.getUniqueId(), amount)) {
            messages.send(payer, "economie.pay-fonds-insuffisants");
            return;
        }

        double seuil = config.get().getDouble("economie.pay-confirmation-seuil", Double.MAX_VALUE);
        if (amount < seuil) {
            executeTransfer(payer, targetUuid, targetName, amount);
            return;
        }

        long expirationSeconds = config.get().getLong("economie.pay-confirmation-expiration-secondes", 30);
        long expiresAt = System.currentTimeMillis() + expirationSeconds * 1000L;
        pendingPayments.set(payer.getUniqueId(), new PendingPayment(targetUuid, targetName, amount, expiresAt));

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("montant", economyManager.format(amount));
        placeholders.put("joueur", targetName);
        payer.sendMessage(messages.get("economie.pay-confirmation-demande", placeholders));

        String clicText = messages.raw("economie.pay-confirmation-clic")
                .replace("{expiration}", String.valueOf(expirationSeconds));
        String survolText = messages.raw("economie.pay-confirmation-survol")
                .replace("{montant}", economyManager.format(amount))
                .replace("{joueur}", targetName);

        // Les textes issus de MessageManager contiennent des codes couleur legacy (section-sign) :
        // on les deserialise en Component avant d'y attacher les evenements clic/survol.
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
        Component clicComponent = legacy.deserialize(clicText)
                .clickEvent(ClickEvent.runCommand("/payconfirm"))
                .hoverEvent(HoverEvent.showText(legacy.deserialize(survolText)));
        payer.sendMessage(clicComponent);
    }

    private void executeTransfer(Player payer, UUID targetUuid, String targetName, double amount) {
        boolean success = economyManager.transfer(payer.getUniqueId(), targetUuid, amount);
        if (!success) {
            messages.send(payer, "economie.pay-fonds-insuffisants");
            return;
        }

        lastPayMillis.put(payer.getUniqueId(), System.currentTimeMillis());

        Map<String, String> senderPlaceholders = new HashMap<>();
        senderPlaceholders.put("joueur", targetName);
        senderPlaceholders.put("montant", economyManager.format(amount));
        messages.send(payer, "economie.pay-envoye", senderPlaceholders);

        Player onlineTarget = Bukkit.getPlayer(targetUuid);
        if (onlineTarget != null) {
            Map<String, String> targetPlaceholders = new HashMap<>();
            targetPlaceholders.put("joueur", payer.getName());
            targetPlaceholders.put("montant", economyManager.format(amount));
            messages.send(onlineTarget, "economie.pay-recu", targetPlaceholders);
        }
    }

    public void confirmPayment(Player payer) {
        PendingPayment payment = pendingPayments.consume(payer.getUniqueId());
        if (payment == null) {
            // consume() renvoie null si aucun paiement n'est en attente OU si celui-ci a expire
            messages.send(payer, "economie.pay-confirmation-aucune");
            return;
        }
        messages.send(payer, "economie.pay-confirmation-validee");
        executeTransfer(payer, payment.targetUuid(), payment.targetName(), payment.amount());
    }
}
