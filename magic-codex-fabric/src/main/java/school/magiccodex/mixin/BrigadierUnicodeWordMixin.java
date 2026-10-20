package school.magiccodex.mixin;

import com.mojang.brigadier.StringReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 2026-10-20 명령어 정리: 서버가 한글 이름 칸(마법·NPC·경기장 등)을 Brigadier {@code word()}로 보낸다.
 * 바닐라 클라이언트의 {@code word()}는 0-9 A-Z a-z _ - . + 만 받아서 한글을 빨간 오류로 표시하므로,
 * 유니코드 글자·숫자({@link Character#isLetterOrDigit(char)}, 한글 포함)도 따옴표 없는 단어로 받게 한다.
 * Brigadier는 Minecraft 라이브러리라 Fabric Loader(Knot)가 로드·변환한다. 난독화되지 않은 이름이라 remap=false.
 */
@Mixin(value = StringReader.class, remap = false)
public abstract class BrigadierUnicodeWordMixin {
    @Inject(method = "isAllowedInUnquotedString(C)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void magiccodex$unicodeWord(char c, CallbackInfoReturnable<Boolean> result) {
        if (Character.isLetterOrDigit(c)) result.setReturnValue(true);
    }
}
