package com.adventurecity.jobs.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Loads the Arabic message file and formats messages.
 *
 * <p>The file is always read as UTF-8 explicitly - reading it with the platform default charset
 * turns every Arabic string into mojibake on servers that boot with a non-UTF-8 locale.</p>
 */
public final class Msg {

    private static final DecimalFormat NUMBER = new DecimalFormat("#,###");

    private final File file;
    private final Logger logger;

    private YamlConfiguration config;
    private String prefix = "";

    public Msg(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        load();
    }

    public void load() {
        config = new YamlConfiguration();
        if (file.isFile()) {
            Reader reader = null;
            try {
                reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
                config = YamlConfiguration.loadConfiguration(reader);
            } catch (IOException ex) {
                logger.warning("[ACRPJobs] Could not read " + file.getName() + ": " + ex.getMessage());
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ignored) {
                        // nothing useful to do
                    }
                }
            }
        }
        prefix = color(config.getString("prefix", ""));
    }

    /** Returns the coloured message, or the path itself when the key is missing (so it is obvious in game). */
    public String get(String path) {
        String value = config.getString(path);
        if (value == null) {
            logger.warning("[ACRPJobs] Missing message key: " + path);
            return ChatColor.RED + path;
        }
        return color(value);
    }

    /** Same as {@link #get(String)} but replaces %key% placeholders from alternating key/value pairs. */
    public String get(String path, Object... placeholders) {
        String value = get(path);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            value = value.replace("%" + placeholders[i] + "%", String.valueOf(placeholders[i + 1]));
        }
        return value;
    }

    public List<String> getList(String path) {
        List<String> out = new ArrayList<String>();
        for (String line : config.getStringList(path)) {
            out.add(color(line));
        }
        return out;
    }

    public void send(CommandSender target, String path, Object... placeholders) {
        String value = get(path, placeholders);
        if (value.isEmpty()) {
            return;
        }
        target.sendMessage(prefix + value);
    }

    /** Sends an already-formatted line with the plugin prefix. */
    public void sendRaw(CommandSender target, String line) {
        target.sendMessage(prefix + color(line));
    }

    public String prefix() {
        return prefix;
    }

    public static String color(String input) {
        return input == null ? "" : ChatColor.translateAlternateColorCodes('&', input);
    }

    public static List<String> color(List<String> input) {
        List<String> out = new ArrayList<String>();
        if (input != null) {
            for (String line : input) {
                out.add(color(line));
            }
        }
        return out;
    }

    /** 12500 -> "12,500" */
    public static String number(long value) {
        synchronized (NUMBER) {
            return NUMBER.format(value);
        }
    }
}
