package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.focus.FocusScreen;

/**
 * Spatial GUI draws the screens it is set to take into a texture of its own and hangs it in the world before the player's eyes.
 * At a station that goes wrong both ways: the station's own screen loses what it paints over the scene (a book's contents), and a
 * screen opened over the station (a recipe viewer's page) hangs where the player's eyes are while the camera is held over the
 * block, out of view or askew, with no way to turn to it. While a station holds the camera, Spatial GUI is told to leave every
 * screen flat; elsewhere it keeps working as the player set it. This method decides that for all of Spatial GUI (its camera,
 * mouse and drawing ask it). Without Spatial GUI the class is missing and nothing is applied. Matched by name: the Fabric jars
 * name the screen class in intermediary names.
 */
@Pseudo
@Mixin(targets = "org.tastytrash.spatialGUI.client.SpatialGUIClient", remap = false)
abstract class SpatialGuiMixin {
    @Inject(method = "shouldHookScreen", at = @At("HEAD"), cancellable = true, require = 0)
    private static void diegesis$leaveStationFlat(Screen screen, CallbackInfoReturnable<Boolean> cir) {
        if (screen instanceof FocusScreen || FocusController.get().holdsScreen()) {
            cir.setReturnValue(false);
        }
    }
}
