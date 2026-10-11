package kr.chacademy.storyplugin;
/** Fixed self-only effects for the current client story. No commands, targets or amounts from the network. */
final class LocalStoryRewards {
    record Effect(String affinityNpc,int affinityDelta,String discoveryEvent,double discoveryAmount) {}
    static Effect resolve(String key) {
        return switch(key) {
            case "ch1-2.e_s11" -> new Effect("teacher_ellna",-1,null,0);
            case "ch1-3.end" -> new Effect(null,0,"jump.sprinting",30);
            default -> null;
        };
    }
}
