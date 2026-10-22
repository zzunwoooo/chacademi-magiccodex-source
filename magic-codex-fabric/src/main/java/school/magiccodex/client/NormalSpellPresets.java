package school.magiccodex.client;
import java.nio.file.*;import java.io.*;import java.util.Properties;
/** Existing key-settings.yml is preset one; preset two uses the existing empty six-slot default. */
final class NormalSpellPresets {
 private final Path dir;private int selected=1,preferred=1;private final SpellKeySettings[] prepared=new SpellKeySettings[3];private IOException saveFailure;
 NormalSpellPresets(Path dir){this.dir=dir;try{var p=new Properties();try(var in=Files.newBufferedReader(dir.resolve("normal-preset.properties"))){p.load(in);}preferred="2".equals(p.getProperty("selected"))?2:1;}catch(IOException|IllegalArgumentException ignored){}}
 int selected(){return selected;}int preferred(){return preferred;}Path path(){return path(selected);}Path path(int index){if(index!=1&&index!=2)throw new IllegalArgumentException();return dir.resolve(index==1?"key-settings.yml":"key-settings-preset2.yml");}
 void resetSession(){selected=1;java.util.Arrays.fill(prepared,null);saveFailure=null;}
 void prepare(int index)throws IOException{prepared[index]=SpellKeySettings.load(path(index)).copy();}
 IOException saveFailure(){return saveFailure;}
 SpellKeySettings load()throws IOException{return SpellKeySettings.load(path());}
 SpellKeySettings confirmed(int index)throws IOException{if(index!=1&&index!=2)throw new IllegalArgumentException();var settings=prepared[index]!=null?prepared[index].copy():SpellKeySettings.load(path(index));java.util.Arrays.fill(prepared,null);selected=index;preferred=index;saveFailure=null;try{Files.createDirectories(dir);var target=dir.resolve("normal-preset.properties");var tmp=Files.createTempFile(dir,"normal-preset-",".tmp");try{Files.writeString(tmp,"selected="+index+"\n");try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}catch(IOException e){saveFailure=e;}return settings;}
}
