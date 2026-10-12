package school.magiccodex.mixin;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import school.magiccodex.client.CastingClient;
/** Only thumb buttons enter the common gameplay-gated casting input path. */
@Mixin(Mouse.class)
public abstract class CodexMouseMixin {
 @Inject(method="onMouseButton",at=@At("HEAD"),cancellable=true)
 private void magiccodex$thumb(long window,int button,int action,int modifiers,CallbackInfo ci){
  var client=MinecraftClient.getInstance();
  if(window==client.getWindow().getHandle()&&CastingClient.handleMouse(client,button,action))ci.cancel();
 }
}
