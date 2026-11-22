package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.menu.MenuSession;

/**
 * Takes the menu a menu-backed station expects without making its screen, and notes when the server closes it. Injected
 * at calls vanilla reaches on the game thread; with no menu station open everything goes on as in vanilla. Slots the
 * server's menu has beyond the station's are left out ({@link MenuSession#beyondMenu}).
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
    @Inject(method = "handleOpenScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/MenuScreens;create(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/client/Minecraft;ILnet/minecraft/network/chat/Component;)V"),
            cancellable = true)
    private void diegesis$takeStationMenu(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (MenuSession.get().onOpenScreen(packet.getType(), packet.getContainerId())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerClose", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;clientSideCloseContainer()V"))
    private void diegesis$noteServerClose(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        MenuSession.get().onServerClose();
    }

    @Inject(method = "handleContainerSetSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;setItem(IILnet/minecraft/world/item/ItemStack;)V"),
            cancellable = true)
    private void diegesis$skipSlotBeyondStationMenu(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        if (MenuSession.get().beyondMenu(packet.getContainerId(), packet.getSlot())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerContent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;initializeContents(ILjava/util/List;Lnet/minecraft/world/item/ItemStack;)V"),
            cancellable = true)
    private void diegesis$fitContentsToStationMenu(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (MenuSession.get().fitContents(packet.getContainerId(), packet.getStateId(), packet.getItems(), packet.getCarriedItem())) {
            ci.cancel();
        }
    }
}
