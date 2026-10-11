package kr.chacademy.story.dialogue;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Per-account local progress. Persist before issuing ordinary player commands; no server balance writes. */
public final class LocalDialogueStore {
    private final Path file;
    private Properties data=new Properties();
    public LocalDialogueStore(Path file)throws IOException {
        this.file=file;
        if(Files.exists(file))try(var reader=Files.newBufferedReader(file)){data.load(reader);}
        for(String key:data.stringPropertyNames())if(!key.startsWith("affinity.")&&!"true".equals(data.getProperty(key)))throw new IOException("invalid local progress record");
        for(String key:data.stringPropertyNames())if(key.startsWith("affinity."))
            try{Integer.parseInt(data.getProperty(key));}catch(NumberFormatException e){throw new IOException("invalid local affinity record",e);}
    }
    public boolean done(String id){return "true".equals(data.getProperty("done."+id));}
    public Map<String,Integer> affinity(){Map<String,Integer> out=new HashMap<>();for(String k:data.stringPropertyNames())if(k.startsWith("affinity."))out.put(k.substring(9),Integer.parseInt(data.getProperty(k)));return out;}
    public boolean claimEvent(String id,String event)throws IOException {return claim(id,"event."+id+"."+event,List.of());}
    public boolean hasAffinity(String id,String key){return data.containsKey("gain."+id+"."+key);}
    public boolean claimAffinity(String id,String key,List<Dialogue.AffinityChange> changes)throws IOException {return claim(id,"gain."+id+"."+key,changes);}
    private boolean claim(String id,String key,List<Dialogue.AffinityChange> changes)throws IOException {
        if(done(id)||data.containsKey(key))return false;
        Properties next=copy();next.setProperty(key,"true");
        for(var change:changes){String k="affinity."+change.npc();int before=Integer.parseInt(next.getProperty(k,String.valueOf(Affinity.DEFAULT)));next.setProperty(k,String.valueOf((int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,(long)before+change.add()))));}
        save(next);return true;
    }
    public boolean complete(String id)throws IOException {
        if(done(id))return false;Properties next=copy();next.setProperty("done."+id,"true");save(next);return true;
    }
    private Properties copy(){Properties next=new Properties();next.putAll(data);return next;}
    private void save(Properties next)throws IOException {
        Files.createDirectories(file.getParent());Path tmp=Files.createTempFile(file.getParent(),"dialogue-",".tmp");
        try{try(var writer=Files.newBufferedWriter(tmp)){next.store(writer,"Local dialogue progress; commands are attempts, not confirmed server rewards");}
            Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);data=next;
        }finally{Files.deleteIfExists(tmp);}
    }
}
