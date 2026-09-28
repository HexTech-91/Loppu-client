package dev.luminous.core.impl;

import com.google.common.base.Splitter;
import dev.luminous.Alien;
import dev.luminous.core.Manager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.IOUtils;

public class KitConfigManager extends Manager {
    public static File kitsFile = getFile("kits.txt");
    private final Hashtable<String, String> kitSettings = new Hashtable<>();
    private String currentKit = "";

    public KitConfigManager() {
        this.load();
    }

    public void load() {
        kitSettings.clear();

        Splitter COLON_SPLITTER = Splitter.on(':');
        try {
            if (kitsFile.exists()) {
                List<String> list = IOUtils.readLines(new FileInputStream(kitsFile), StandardCharsets.UTF_8);
                for (String s : list) {
                    try {
                        Iterator<String> iterator = COLON_SPLITTER.limit(2).split(s).iterator();
                        kitSettings.put(iterator.next(), iterator.next());
                    } catch (Exception e) {
                        System.out.println("Skipping bad kit option: " + s);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("[Alien] Failed to load kit settings from file");
        }

        // Load current kit
        currentKit = getString("current_kit", "");
    }

    public void save() {
        PrintWriter printwriter = null;
        try {
            printwriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(kitsFile), StandardCharsets.UTF_8));
            // Save current kit
            printwriter.println("current_kit:" + currentKit);
            
            // Save other kit settings if needed
            for (String key : kitSettings.keySet()) {
                if (!key.equals("current_kit")) {
                    printwriter.println(key + ":" + kitSettings.get(key));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("[Alien] Failed to save kit settings");
        } finally {
            IOUtils.closeQuietly(printwriter);
        }
    }

    public void setCurrentKit(String kitName) {
        this.currentKit = kitName;
        kitSettings.put("current_kit", kitName);
        save();
    }

    public String getCurrentKit() {
        return currentKit;
    }

    public int getInt(String setting, int defaultValue) {
        String s = kitSettings.get(setting);
        return (s != null && isInteger(s)) ? Integer.parseInt(s) : defaultValue;
    }

    public float getFloat(String setting, float defaultValue) {
        String s = kitSettings.get(setting);
        return (s != null && isFloat(s)) ? Float.parseFloat(s) : defaultValue;
    }

    public boolean getBoolean(String setting) {
        String s = kitSettings.get(setting);
        return Boolean.parseBoolean(s);
    }

    public boolean getBoolean(String setting, boolean defaultValue) {
        String s = kitSettings.get(setting);
        return (s != null) ? Boolean.parseBoolean(s) : defaultValue;
    }

    public String getString(String setting) {
        return kitSettings.get(setting);
    }

    public String getString(String setting, String defaultValue) {
        String s = kitSettings.get(setting);
        return (s != null) ? s : defaultValue;
    }

    public boolean isInteger(String str) {
        return str.matches("^[-+]?[\\d]*$");
    }

    public boolean isFloat(String str) {
        return str.matches("^[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?$");
    }
}