package dev.portablevfx.client.render;
import dev.portablevfx.client.definition.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
/** Includes all verified definitions, dependency bytes, validation result and operator limits. */
final class ResourceFingerprint {
 private ResourceFingerprint(){}
 static String of(EffectLibrary.Snapshot snapshot,LoadLimits limits){
  try{
   var h=MessageDigest.getInstance("SHA-256");text(h,limits.toString());text(h,Boolean.toString(snapshot.valid()));
   snapshot.definitions().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{text(h,e.getKey().toString());text(h,e.getValue().toString());});
   snapshot.assets().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{text(h,e.getKey().toString());h.update(ByteBuffer.allocate(4).putInt(e.getValue().length).array());h.update(e.getValue());});
   return HexFormat.of().formatHex(h.digest());
  }catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
 }
 private static void text(MessageDigest h,String s){byte[] b=s.getBytes(StandardCharsets.UTF_8);h.update(ByteBuffer.allocate(4).putInt(b.length).array());h.update(b);}
}
