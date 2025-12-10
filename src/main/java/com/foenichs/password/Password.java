package com.foenichs.password;

import org.bukkit.plugin.java.JavaPlugin;

public final class Password extends JavaPlugin {
    private PasswordLogic logic;

    @Override
    public void onEnable() {
        logic = new PasswordLogic(this);
        logic.onEnable();
        getServer().getPluginManager().registerEvents(logic, this);
    }

    @Override
    public void onDisable() {
        if (logic != null) {
            logic.onDisable();
        }
    }
}