package school.magiccodex.mixin;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.BakedGlyph;
import net.minecraft.util.Identifier;
import school.magiccodex.client.ItemIconTextures;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(FontStorage.class)
public abstract class ChatIconGlyphMixin {
 @Shadow @Final private Identifier id;
 @Inject(method="getBaked",at=@At("HEAD"),cancellable=true)
 private void magiccodex$chatIcon(int codepoint,CallbackInfoReturnable<BakedGlyph> cir){
  if(!id.equals(Identifier.of("magiccodex","chat")))return;
  var glyph=ItemIconTextures.chatGlyph(codepoint);if(glyph!=null)cir.setReturnValue(glyph);
 }
}
