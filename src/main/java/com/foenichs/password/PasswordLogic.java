package com.foenichs.password;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("UnstableApiUsage")
public final class PasswordLogic implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, CompletableFuture<Boolean>> awaiting = new ConcurrentHashMap<>();
    private static final Key CLICK_KEY = Key.key("password:check");
    private String serverPassword;
    private boolean operatorBypass;
    private boolean whitelistBypass;

    public PasswordLogic(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        plugin.saveDefaultConfig();
        serverPassword = plugin.getConfig().getString("password", "");
        operatorBypass = plugin.getConfig().getBoolean("operator_bypass", true);
        whitelistBypass = plugin.getConfig().getBoolean("whitelist_bypass", true);
    }

    public void onDisable() {
        awaiting.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerConfigure(AsyncPlayerConnectionConfigureEvent event) {
        PlayerConfigurationConnection connection = event.getConnection();
        UUID uuid = connection.getProfile().getId();
        if (uuid == null) return;

        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (whitelistBypass && offline.isWhitelisted()) {
            return;
        }
        if (operatorBypass && offline.isOp()) {
            return;
        }

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(
                        DialogBase.builder(
                                        Component.empty()
                                                .append(Component.object(
                                                        ObjectContents.sprite(Key.key("items"), Key.key("item/trial_key"))
                                                ))
                                                .append(Component.text(" Password", NamedTextColor.WHITE))
                                )
                                .canCloseWithEscape(false)
                                .body(List.of(
                                        DialogBody.plainMessage(Component.text("This server requires you to enter a password before joining."))
                                ))
                                .inputs(List.of(
                                        DialogInput.text("pw", Component.text("Enter Password", NamedTextColor.GRAY))
                                                .maxLength(128)
                                                .width(200)
                                                .build()
                                ))
                                .build()
                )
                .type(
                        DialogType.multiAction(List.of(
                                ActionButton.create
                                        (Component.text("Submit"),
                                                null,
                                                80,
                                                DialogAction.customClick(CLICK_KEY, null)),
                                ActionButton.create
                                        (Component.text("Cancel"),
                                                null,
                                                80,
                                                DialogAction.customClick(Key.key("password:cancel"), null))
                        )).build()
                )
        );

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        future.completeOnTimeout(false, 1, TimeUnit.MINUTES);

        awaiting.put(uuid, future);

        Audience audience = connection.getAudience();
        audience.showDialog(dialog);

        if (!future.join()) {
            audience.closeDialog();
            connection.disconnect(Component.text("Server requires correct password."));
        }

        awaiting.remove(uuid);
    }

    @EventHandler
    public void onClick(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof PlayerConfigurationConnection cfg)) return;

        UUID uuid = cfg.getProfile().getId();
        if (uuid == null) return;

        Key id = event.getIdentifier();

        if (id.equals(Key.key("password:cancel"))) {
            cfg.disconnect(Component.text("Server requires correct password."));
            CompletableFuture<Boolean> fCancel = awaiting.get(uuid);
            if (fCancel != null) fCancel.complete(false);
            return;
        }

        if (!id.equals(CLICK_KEY)) return;

        DialogResponseView view = event.getDialogResponseView();
        if (view == null) return;

        String entered = view.getText("pw");
        if (entered == null) entered = "";

        boolean correct = serverPassword.equals(entered);

        CompletableFuture<Boolean> f = awaiting.get(uuid);
        if (f != null) f.complete(correct);
    }

    @EventHandler
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        awaiting.remove(event.getPlayerUniqueId());
    }
}