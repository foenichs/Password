package com.foenichs.password;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public final class Password extends JavaPlugin {
    private PasswordLogic logic;

    @Override
    public void onEnable() {
        logic = new PasswordLogic(this);
        logic.onEnable();
        getServer().getPluginManager().registerEvents(logic, this);
        new Metrics(this, 33587);
    }

    @Override
    public void onDisable() {
        if (logic != null) {
            logic.onDisable();
        }
    }
}