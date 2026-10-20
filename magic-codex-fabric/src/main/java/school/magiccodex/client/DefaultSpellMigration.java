package school.magiccodex.client;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Updates only recognized shipped defaults. Local edits and extra files remain untouched. */
final class DefaultSpellMigration {
    static final String VERSION="spells-fish-trap-20261020";
    private static final String ROOT="/assets/magiccodex/default-spells/";
    private static final Set<String> ARCANA_IDS=Set.of("shadow_double","echo_reading","sealing_thread","shadow_exchange","forbidden_contract","life_siphon","drowsiness","misfortune_whisper","hungry_shadow","curse_transfer","discord","night_chain","pain_deferral","black_harvest","ink_fingertip","shadow_nail","night_lullaby","loosen_grasp","healing_seal","shadow_scales","warning_thread","wound_accounting","inverted_offering","ghost_tool","bell_of_fear","eclipse_eye","void_maw","landing_airbrake","hushed_soles","thorn_manners","cinder_skipring","droplet_relay","folding_tideplane");
    static boolean managedHistoryName(String name){
        if(name.startsWith("149_"))return false;
        if(name.matches("(?:10[1-9]|1[1-5][0-9]|16[0-7])_.*\\.yml"))return true;
        if(!name.matches("(?:16[89]|1[7-9][0-9]|20[0-9]|21[0-4])_.*\\.yml"))return false;
        String id=name.substring(name.indexOf('_')+1,name.length()-4);
        return ARCANA_IDS.contains(id); // Excludes all fourteen deferred IDs and legacy wind entries sharing 200-series numbers.
    }
    static String hash(byte[] bytes) {
        String text=new String(bytes,StandardCharsets.UTF_8).replace("\r\n","\n");
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static byte[] resource(String name)throws IOException {
        try(var in=DefaultSpellMigration.class.getResourceAsStream(ROOT+name)){
            if(in==null)throw new IOException("Missing default resource: "+name);return in.readAllBytes();
        }
    }
    static void install(Path directory)throws IOException {
        Map<String,Set<String>> history=new HashMap<>();
        for(String line:new String(resource("known-hashes.tsv"),StandardCharsets.UTF_8).lines().toList()){
            String[] parts=line.split("\t");if(parts.length==2 && managedHistoryName(parts[0]))history.computeIfAbsent(parts[0],k->new HashSet<>()).add(parts[1]);
        }
        Map<String,byte[]> defaults=new LinkedHashMap<>();
        for(String id:new String(resource("index.txt"),StandardCharsets.UTF_8).lines().filter(s->!s.isBlank()).toList())defaults.put(id+".yml",resource(id+".yml"));
        migrate(directory,defaults,history,VERSION);
    }
    static void migrate(Path directory,Map<String,byte[]> defaults,Map<String,Set<String>> history,String version)throws IOException {
        if(Files.isSymbolicLink(directory))throw new IOException("Refusing symlink spell directory");
        Files.createDirectories(directory);
        for(var entry:defaults.entrySet()){
            String name=entry.getKey();if(!name.matches("[a-zA-Z0-9_-]+\\.yml"))throw new IOException("Invalid default filename");
            Path file=directory.resolve(name);byte[] next=entry.getValue();
            if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)){
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>65536)continue;
                byte[] current=Files.readAllBytes(file);String oldHash=hash(current);
                if(oldHash.equals(hash(next))||!history.getOrDefault(name,Set.of()).contains(oldHash))continue;
                // Recognized shipped bytes are reproducible; this migration creates no backup, as requested.
            }
            atomicWrite(file,next);
        }
        atomicWrite(directory.resolve(".defaults-version"),version.getBytes(StandardCharsets.UTF_8));
    }
    private static void atomicWrite(Path destination,byte[] bytes)throws IOException {
        Path tmp=Files.createTempFile(destination.getParent(),".defaults-",".tmp");
        try{Files.write(tmp,bytes);try{Files.move(tmp,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,destination,StandardCopyOption.REPLACE_EXISTING);}}
        finally{Files.deleteIfExists(tmp);}
    }
}
