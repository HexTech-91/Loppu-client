package dev.luminous.mod.gui.nakano.click;

import dev.luminous.Alien;
import dev.luminous.mod.modules.Module;

import java.util.Comparator;
import java.util.List;

/** The visible module categories. Ported from Nakano's Opal-style ClickGUI. */
enum GuiCategory {
    CLIENT("Client", Module.Category.Client),
    COMBAT("Combat", Module.Category.Combat),
    EXPLOIT("Exploit", Module.Category.Exploit),
    MISC("Misc", Module.Category.Misc),
    MOVEMENT("Movement", Module.Category.Movement),
    OTHER("Mace", Module.Category.Mace),
    PLAYER("Player", Module.Category.Player),
    RENDER("Render", Module.Category.Render);

    private final String displayName;
    private final Module.Category category;

    GuiCategory(String displayName, Module.Category category) {
        this.displayName = displayName;
        this.category = category;
    }

    String displayName() {
        return displayName;
    }

    List<GuiModule> modules() {
        if (Alien.MODULE == null) {
            return List.of();
        }
        return Alien.MODULE.getModules().stream()
                .filter(module -> module.getCategory() == category)
                .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER))
                .map(GuiModule::new)
                .toList();
    }
}