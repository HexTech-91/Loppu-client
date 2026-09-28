package dev.luminous.mod.modules.impl.client;

import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

public class SkinChanger extends Module {
    public static SkinChanger INSTANCE;
    public final EnumSetting<SkinChanger.Mode> mode = this.add(new EnumSetting("Mode", SkinChanger.Mode.AllPlayers));
    public final BooleanSetting self = this.add(new BooleanSetting("Self", false, () -> this.mode.getValue() == SkinChanger.Mode.AllPlayers));
    public final EnumSetting<BuiltinSkin> builtinSkin = this.add(new EnumSetting<>("BuiltinSkin", BuiltinSkin.HCQ));

    public SkinChanger() {
        super("SkinChanger", Category.Client);
        this.setChinese("皮肤替换");
        INSTANCE = this;
    }

    public boolean shouldReplaceSkin(AbstractClientPlayerEntity player) {
        if (this.mode.getValue() == SkinChanger.Mode.AllPlayers) {
            return this.self.getValue() || player != mc.player;
        } else {
            return this.mode.getValue() == SkinChanger.Mode.Self ? player == mc.player : false;
        }
    }

    public Identifier getSelectedSkin() {
        return builtinSkin.getValue().getIdentifier();
    }

    public SkinTextures getCustomSkinTextures(AbstractClientPlayerEntity player) {
        if (!shouldReplaceSkin(player)) return null;

        try {
            Identifier skinId = getSelectedSkin();
            return new SkinTextures(
                    skinId,
                    "",
                    null,
                    null,
                    SkinTextures.Model.WIDE,
                    false
            );
        } catch (Exception e) {
            return null;
        }
    }

    public static enum Mode {
        AllPlayers,
        Self;
    }

    public enum BuiltinSkin {
        HCQ("hcq", "textures/skin/hcq.png"),
        PIFU_1("1", "textures/pifu/1.png"),
        PIFU_2("2", "textures/pifu/2.png"),
        PIFU_3("3", "textures/pifu/3.png"),
        PIFU_4("4", "textures/pifu/4.png"),
        PIFU_5("5", "textures/pifu/5.png"),
        PIFU_6("6", "textures/pifu/6.png"),
        PIFU_7("7", "textures/pifu/7.png"),
        PIFU_8("8", "textures/pifu/8.png"),
        PIFU_9("9", "textures/pifu/9.png"),
        PIFU_10("10", "textures/pifu/10.png"),
        PIFU_11("11", "textures/pifu/11.png"),
        BANDIANGOU("bandiangou", "textures/pifu/bandiangou.png");

        private final String name;
        private final String path;
        private final Identifier identifier;

        BuiltinSkin(String name, String path) {
            this.name = name;
            this.path = path;
            this.identifier = Identifier.tryParse("loppu", path);
        }

        public String getSkinName() {
            return name;
        }

        public Identifier getIdentifier() {
            return identifier;
        }
    }
}