package dev.luminous.asm;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class AlienMixinPlugin implements IMixinConfigPlugin {
    private static final String HELD_ITEM_RENDERER_MIXIN = "dev.luminous.asm.mixins.MixinHeldItemRenderer";
    private static final String HELD_ITEM_RENDERER_TARGET = "net.minecraft.client.render.item.HeldItemRenderer";
    private static final String FORCE_PROPERTY = "tide.forceHeldItemRendererMixin";
    private static final Set<String> SODIUM_MIXINS = Set.of(
            "dev.luminous.asm.mixins.sodium.SodiumBlockOcclusionCacheMixin",
            "dev.luminous.asm.mixins.sodium.SodiumBlockRendererMixin",
            "dev.luminous.asm.mixins.sodium.SodiumFluidRendererImplMixin"
    );
    private static final Set<String> CONFLICT_MOD_IDS = Set.of(
            "meteor-client",
            "liquidbounce",
            "wurstclient",
            "rise",
            "vape",
            "aristois",
            "tenacity",
            "future",
            "lunarclient",
            "mio",
            "mioclient",
            "miofucker"
    );
    private static final Set<String> CONFLICT_CLASS_NAMES = Set.of(
            "meteordevelopment.meteorclient.MeteorClient",
            "net.ccbluex.liquidbounce.LiquidBounce",
            "net.wurstclient.WurstClient",
            "me.earth.phobot.Phobot",
            "com.alan.clients.Client"
    );
    private boolean otherClientHandlesHeldItemRenderer;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (SODIUM_MIXINS.contains(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded("sodium");
        }
        if (!HELD_ITEM_RENDERER_MIXIN.equals(mixinClassName)) {
            return true;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        otherClientHandlesHeldItemRenderer = otherTargets.stream()
                .map(target -> target.replace('/', '.'))
                .anyMatch(HELD_ITEM_RENDERER_TARGET::equals);
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private boolean hasConflictingClient() {
        FabricLoader loader = FabricLoader.getInstance();
        for (String modId : CONFLICT_MOD_IDS) {
            if (loader.isModLoaded(modId)) {
                return true;
            }
        }
        for (ModContainer mod : loader.getAllMods()) {
            String id = mod.getMetadata().getId().toLowerCase(Locale.ROOT);
            String name = mod.getMetadata().getName().toLowerCase(Locale.ROOT);
            if (matchesConflict(id) || matchesConflict(name)) {
                return true;
            }
        }
        for (String className : CONFLICT_CLASS_NAMES) {
            if (isClassPresent(className)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesConflict(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("meteor")
                || normalized.contains("liquidbounce")
                || normalized.contains("wurst")
                || normalized.contains("aristois")
                || normalized.contains("tenacity")
                || normalized.contains("future")
                || normalized.contains("rise")
                || normalized.contains("vape")
                || normalized.contains("lunar")
                || normalized.equals("mio")
                || normalized.contains("mioclient")
                || normalized.contains("mio client");
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, AlienMixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}