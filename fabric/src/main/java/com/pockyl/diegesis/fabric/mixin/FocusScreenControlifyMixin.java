package com.pockyl.diegesis.fabric.mixin;

import com.pockyl.diegesis.fabric.compat.ControlifyCompat;
import com.pockyl.diegesis.impl.client.focus.FocusScreen;
import dev.isxander.controlify.api.vmousesnapping.ISnapBehaviour;
import dev.isxander.controlify.api.vmousesnapping.SnapPoint;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;

@Mixin(FocusScreen.class)
abstract class FocusScreenControlifyMixin implements ISnapBehaviour {
    @Override
    public void controlify$collectSnapPoints(Consumer<SnapPoint> consumer) {
        ControlifyCompat.collectSnapPoints(consumer);
    }
}
