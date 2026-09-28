package dev.luminous.mod.modules.impl.client;

import dev.luminous.mod.modules.Module;
import java.io.PrintWriter;

public class IRC
        extends Module {
    public static PrintWriter printWriter;
    public static volatile boolean connect;

    public IRC() {
        super("IRC", Module.Category.Client);
    }
}