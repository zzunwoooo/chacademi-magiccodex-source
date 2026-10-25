package school.magiccodex.paper;
final class ArtifactOwnershipStore {
 interface Connections { java.sql.Connection open() throws java.sql.SQLException; }
 private final Connections connections=null;
 private boolean pityTable;
 java.util.Map<java.util.UUID,Integer> opening$pity(java.util.Collection<java.util.UUID> players) throws java.sql.SQLException {
  var result=new java.util.HashMap<java.util.UUID,Integer>();for(var id:players)result.put(id,0);
  if(!pityTable||players.isEmpty())return java.util.Map.copyOf(result);
  var ids=java.util.List.copyOf(result.keySet());
  try(var connection=connections.open()){
   for(int first=0;first<ids.size();first+=200){var part=ids.subList(first,Math.min(ids.size(),first+200));
    try(var query=connection.prepareStatement("SELECT player,pulls FROM codex_artifact_pity WHERE player IN ("+String.join(",",java.util.Collections.nCopies(part.size(),"?"))+")")){
     for(int i=0;i<part.size();i++)query.setString(i+1,part.get(i).toString());
     try(var rows=query.executeQuery()){while(rows.next()){var id=java.util.UUID.fromString(rows.getString(1));if(result.containsKey(id))result.put(id,Math.max(0,rows.getInt(2)));}}
    }
   }
  }
  return java.util.Map.copyOf(result);
 }
}
