package dev.luminous.mod.gui.nakano;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Lazy font repository ported from Nakano's Opal-style FontRepository.
 * Loads fonts from the loppu mod assets.
 */
public final class FontRepository {
    private static final Map<String, NvgFont> FONTS = new HashMap<>();

    private FontRepository() {
    }

    public static NvgFont get(String name) {
        return FONTS.computeIfAbsent(name, FontRepository::load);
    }

    public static void close() {
        FONTS.values().forEach(NvgFont::close);
        FONTS.clear();
    }

    private static NvgFont load(String name) {
        Path path = FabricLoader.getInstance()
                .getModContainer("loppu")
                .flatMap(container -> container.findPath("assets/loppu/fonts/" + name + ".ttf"))
                .orElseThrow(() -> new IllegalStateException("Missing Nakano font: " + name));

        try {
            InputStream input = Files.newInputStream(path);
            return new NvgFont(name, input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load Nakano font: " + name, exception);
        }
    }
}