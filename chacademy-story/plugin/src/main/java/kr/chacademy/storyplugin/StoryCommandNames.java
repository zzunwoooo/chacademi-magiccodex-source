package kr.chacademy.storyplugin;
import java.util.*;
/** Korean syntax adapter; permissions and execution remain in the existing handlers. */
public final class StoryCommandNames {
    private StoryCommandNames() {}
    public static String action(String s) {
        return switch(s.toLowerCase(Locale.ROOT)) {
            case "재생"->"play";case "중지"->"stop";case "취소"->"clear";
            case "리로드"->"reload";case "서버리로드"->"serverreload";
            case "기록"->"ledger";case "기록초기화"->"reset";case "목록"->"list";
            default->s.toLowerCase(Locale.ROOT);
        };
    }
    public static String mode(String s) {
        return switch(s){case "자동"->"auto";case "건너뛰기허용"->"skip";case "건너뛰기금지"->"noskip";default->s;};
    }
    public record Play(String target,String id,String mode) {}
    public static Play play(boolean dialogue,String[] args) {
        if(args.length<3||!action(args[0]).equals("play"))throw new IllegalArgumentException("재생 <유저> <식별자>");
        int end=args.length;String mode="auto";
        if(!dialogue&&end>=4&&Set.of("자동","건너뛰기허용","건너뛰기금지","auto","skip","noskip").contains(args[end-1]))mode=mode(args[--end]);
        String target=String.join(" ",Arrays.copyOfRange(args,1,end-1)),id=args[end-1];
        if(target.isBlank()||!StorySafety.validId(id))throw new IllegalArgumentException("유저와 올바른 식별자를 입력해 주세요.");
        return new Play(target,id,mode);
    }
}
