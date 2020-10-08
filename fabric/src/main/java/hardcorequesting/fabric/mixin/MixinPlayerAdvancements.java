package hardcorequesting.fabric.mixin;

import hardcorequesting.fabric.HardcoreQuestingFabric;
import net.minecraft.advancements.Advancement;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiConsumer;

@Mixin(PlayerAdvancements.class)
public class MixinPlayerAdvancements {
    @Shadow private ServerPlayer player;
    
    @Inject(method = "award",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V",
                     shift = At.Shift.AFTER))
    private void reward(Advancement advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
        for (BiConsumer<ServerPlayer, Advancement> consumer : HardcoreQuestingFabric.ADVANCEMENT) {
            consumer.accept(player, advancement);
        }
    }
}
