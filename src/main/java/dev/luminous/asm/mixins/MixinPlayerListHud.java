/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.sugar.Local
 *  com.llamalad7.mixinextras.sugar.ref.LocalIntRef
 *  net.minecraft.client.gui.hud.PlayerListHud
 *  net.minecraft.client.network.PlayerListEntry
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.At$Shift
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package dev.luminous.asm.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.mod.modules.impl.client.ExtraTab;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={PlayerListHud.class})
public abstract class MixinPlayerListHud {
    @Final
    @Shadow
    private static Comparator<PlayerListEntry> ENTRY_ORDERING;

    @Shadow
    protected abstract List<PlayerListEntry> collectPlayerEntries();

    @Inject(method={"collectPlayerEntries"}, at={@At(value="HEAD")}, cancellable=true)
    private void collectPlayerEntriesHook(CallbackInfoReturnable<List<PlayerListEntry>> cir) {
        if (ExtraTab.INSTANCE.isOn()) {
            cir.setReturnValue(Wrapper.mc.player.networkHandler.getListedPlayerListEntries().stream().sorted(ENTRY_ORDERING).limit(ExtraTab.INSTANCE.size.getValueInt()).toList());
        }
    }

    @Inject(method={"render"}, at={@At(value="INVOKE", target="Ljava/lang/Math;min(II)I", shift=At.Shift.BEFORE)})
    private void hookRender(CallbackInfo ci, @Local(ordinal=5) LocalIntRef o, @Local(ordinal=6) LocalIntRef p) {
        int totalPlayers = this.collectPlayerEntries().size();
        int newP = 1;
        int newO = (totalPlayers + newP - 1) / newP;
        while (newO > ExtraTab.INSTANCE.columns.getValueInt()) {
            newO = (totalPlayers + ++newP - 1) / newP;
        }
        o.set(newO);
        p.set(newP);
    }

    @Inject(method={"getPlayerName"}, at={@At(value="RETURN")}, cancellable=true)
    private void getPlayerNameHook(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (!ExtraTab.shouldApplyColor()) {
            return;
        }
        
        String playerName = entry.getProfile().getName();
        Text originalName = cir.getReturnValue();
        
        int customColor = ExtraTab.getPlayerColor(playerName, -1);
        if (customColor == -1) {
            return;
        }
        
        MutableText styledName = Text.literal(originalName.getString())
                .setStyle(Style.EMPTY.withColor(customColor));
        
        cir.setReturnValue(styledName);
    }
}
