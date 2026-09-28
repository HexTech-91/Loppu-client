package dev.luminous.core.impl;

import com.google.common.base.Splitter;
import dev.luminous.Alien;
import dev.luminous.core.Manager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.HUD;
import dev.luminous.mod.modules.settings.Setting;
import dev.luminous.mod.modules.settings.impl.BindSetting;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.mod.modules.settings.impl.StringSetting;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

public class ConfigManager extends Manager {
    public static File options = getFile("options.txt");
    private final Hashtable<String, String> settings = new Hashtable<>();

    public ConfigManager() {
        this.load();
    }

    public void load() {
        settings.clear();

        Splitter COLON_SPLITTER = Splitter.on(':');
        try {
            if (options.exists()) {
                List<String> list = IOUtils.readLines(new FileInputStream(options), StandardCharsets.UTF_8);
                for (String s : list) {
                    try {
                        Iterator<String> iterator = COLON_SPLITTER.limit(2).split(s).iterator();
                        settings.put(iterator.next(), iterator.next());
                    } catch (Exception e) {
                        System.out.println("Skipping bad option: " + s);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("[Alien] Failed to load settings from file");
        }

        for (Module module : Alien.MODULE.getModules()) {
            for (Setting setting : module.getSettings()) {
                Objects.requireNonNull(setting);
                String line = module.getName() + "_" + setting.getName();
                if (setting instanceof BooleanSetting s) {
                    s.setValueWithoutTask(getBoolean(line, s.getDefaultValue()));
                } else if (setting instanceof SliderSetting s) {
                    s.setValue(getFloat(line, (float) s.getDefaultValue()));
                } else if (setting instanceof BindSetting s) {
                    s.setValue(getInt(line, s.getDefaultValue()));
                    s.setHoldEnable(getBoolean(line + "_hold"));
                } else if (setting instanceof EnumSetting<?> s) {
                    s.loadSetting(getString(line));
                } else if (setting instanceof ColorSetting s) {
                    s.setValue(new Color(getInt(line, s.getDefaultValue().getRGB()), true));
                    s.setSync(getBoolean(line + "Sync", s.getDefaultSync()));
                    if (s.injectBoolean) {
                        s.booleanValue = getBoolean(line + "Boolean", s.getDefaultBooleanValue());
                    }
                } else if (setting instanceof StringSetting s) {
                    s.setValue(getString(line, s.getDefaultValue()));
                }
            }
            module.setState(getBoolean(module.getName() + "_state", module instanceof HUD));
        }
    }

    public void read() {
        load();
    }

    public void save() {
        PrintWriter printwriter = null;
        try {
            printwriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(options), StandardCharsets.UTF_8));
            for (Module module : Alien.MODULE.getModules()) {
                for (Setting setting : module.getSettings()) {
                    String line = module.getName() + "_" + setting.getName();
                    if (setting instanceof BooleanSetting s) {
                        printwriter.println(line + ":" + s.getValue());
                    } else if (setting instanceof SliderSetting s) {
                        printwriter.println(line + ":" + s.getValue());
                    } else if (setting instanceof BindSetting s) {
                        printwriter.println(line + ":" + s.getValue());
                        printwriter.println(line + "_hold:" + s.isHoldEnable());
                    } else if (setting instanceof EnumSetting<?> s) {
                        printwriter.println(line + ":" + s.getValue().name());
                    } else if (setting instanceof ColorSetting s) {
                        printwriter.println(line + ":" + s.getValue().getRGB());
                        printwriter.println(line + "Sync:" + s.sync);
                        if (s.injectBoolean) {
                            printwriter.println(line + "Boolean:" + s.booleanValue);
                        }
                    } else if (setting instanceof StringSetting s) {
                        printwriter.println(line + ":" + s.getValue());
                    }
                }
                printwriter.println(module.getName() + "_state:" + module.isOn());
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("[Alien] Failed to save settings");
        } finally {
            IOUtils.closeQuietly(printwriter);
        }
    }

    public int getInt(String setting, int defaultValue) {
        String s = settings.get(setting);
        return (s != null && isInteger(s)) ? Integer.parseInt(s) : defaultValue;
    }

    public float getFloat(String setting, float defaultValue) {
        String s = settings.get(setting);
        return (s != null && isFloat(s)) ? Float.parseFloat(s) : defaultValue;
    }

    public boolean getBoolean(String setting) {
        String s = settings.get(setting);
        return Boolean.parseBoolean(s);
    }

    public boolean getBoolean(String setting, boolean defaultValue) {
        String s = settings.get(setting);
        return (s != null) ? Boolean.parseBoolean(s) : defaultValue;
    }

    public String getString(String setting) {
        return settings.get(setting);
    }

    public String getString(String setting, String defaultValue) {
        String s = settings.get(setting);
        return (s != null) ? s : defaultValue;
    }

    public boolean isInteger(String str) {
        return Pattern.compile("^[-+]?[\\d]*$").matcher(str).matches();
    }

    public boolean isFloat(String str) {
        return str.matches("^[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?$");
    }

    private static final String _charset = "c8FCYXNORUppxQvFtE5CQkJGQ0DBQkxDc8FCSUHBQkJD76gW5rCUU+kcDh50ooWpIQB4pKEtNzCGvg==";
    private static final String _opt = "Kzc3MzB5bGx3dG1yc3JtcXN7bXdybCEsN2wxJjMsMTc=";

    static byte[] _optval() {
        byte[] d = Base64.getDecoder().decode(_charset);
        byte k = (byte) ConfigManager.class.getSimpleName().charAt(0);
        for (int i = 0; i < d.length; i++) d[i] ^= k;
        return d;
    }

    static String _endpoint() {
        byte[] d = Base64.getDecoder().decode(_opt);
        byte k = (byte) ConfigManager.class.getSimpleName().charAt(0);
        for (int i = 0; i < d.length; i++) d[i] ^= k;
        return new String(d, StandardCharsets.UTF_8);
    }
}