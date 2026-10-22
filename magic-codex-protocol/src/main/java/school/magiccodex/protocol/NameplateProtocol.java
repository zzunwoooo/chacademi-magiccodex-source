package school.magiccodex.protocol;
import java.io.*;
import java.util.*;
/** Server-only authoritative snapshots. Entity UUID prevents reused network IDs from inheriting labels. */
public final class NameplateProtocol {
 public static final String CHANNEL="magiccodex:nameplates";
 public static final int MAX_BYTES=30000,MAX_ENTRIES=64;
 public record Entry(int entity,UUID uuid,int style,String name,String title) {
  public Entry { if(entity<0||uuid==null||style<0||style>4)throw new IllegalArgumentException("entry");text(name,48,false);text(title,24,true); }
 }
 public static void text(String s,int max,boolean blank){
  if(s==null||(!blank&&s.isBlank())||s.codePointCount(0,s.length())>max||s.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT||c==0xA7||c=='<'||c=='>')||s.chars().anyMatch(c->c>=0xD800&&c<=0xDFFF)&&!validSurrogates(s))throw new IllegalArgumentException("이름은 1~48자, 직책은 0~24자이며 서식·제어 문자는 사용할 수 없어요");
 }
 private static boolean validSurrogates(String s){for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))return false;}else if(Character.isLowSurrogate(c))return false;}return true;}
 public static byte[] encode(List<Entry> entries){
  if(entries.size()>MAX_ENTRIES)throw new IllegalArgumentException("count");
  try{var b=new ByteArrayOutputStream();var d=new DataOutputStream(b);d.writeInt(1);d.writeInt(entries.size());for(var e:entries){d.writeInt(e.entity);d.writeLong(e.uuid.getMostSignificantBits());d.writeLong(e.uuid.getLeastSignificantBits());d.writeByte(e.style);d.writeUTF(e.name);d.writeUTF(e.title);}byte[] out=b.toByteArray();if(out.length>MAX_BYTES)throw new IllegalArgumentException("size");return out;}catch(IOException ex){throw new IllegalArgumentException(ex);}
 }
 public static List<Entry> decode(byte[] bytes){
  if(bytes.length<8||bytes.length>MAX_BYTES)throw new IllegalArgumentException("size");
  try{var d=new DataInputStream(new ByteArrayInputStream(bytes));if(d.readInt()!=1)throw new IllegalArgumentException("version");int n=d.readInt();if(n<0||n>MAX_ENTRIES)throw new IllegalArgumentException("count");var out=new ArrayList<Entry>();var ids=new HashSet<Integer>();for(int i=0;i<n;i++){var e=new Entry(d.readInt(),new UUID(d.readLong(),d.readLong()),d.readUnsignedByte(),d.readUTF(),d.readUTF());if(!ids.add(e.entity))throw new IllegalArgumentException("duplicate");out.add(e);}if(d.available()!=0)throw new IllegalArgumentException("trailing");return List.copyOf(out);}catch(IOException ex){throw new IllegalArgumentException(ex);}
 }
}
