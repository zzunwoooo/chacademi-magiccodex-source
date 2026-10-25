import json,copy,yaml
ABSENT=object()
def merge_value(live,old,new,path=''):
 if old is not ABSENT and new==old:return copy.deepcopy(live)
 if live is not ABSENT and live==new:return copy.deepcopy(live)
 if isinstance(new,dict) and (isinstance(old,dict) or old is ABSENT) and (isinstance(live,dict) or live is ABSENT):
  result=copy.deepcopy(live) if live is not ABSENT else {};before=old if isinstance(old,dict) else {}
  for key,value in new.items():
   prior=before.get(key,ABSENT);actual=result.get(key,ABSENT)
   if prior is not ABSENT and prior==value:continue
   result[key]=merge_value(actual,prior,value,path+'.'+str(key))
  return result
 if live is ABSENT or old is not ABSENT and live==old:return copy.deepcopy(new)
 raise ValueError('Resource changed in both operating and source: '+path)
def metadata_union(live,new,path=''):
 if isinstance(live,dict) and isinstance(new,dict):return {key:metadata_union(live[key],new[key],path+'.'+key) if key in live and key in new else live[key] if key in live else new[key] for key in live.keys()|new.keys()}
 if isinstance(live,list) and isinstance(new,list):return live+[x for x in new if x not in live]
 if live!=new:raise ValueError('Conflicting metadata: '+path)
 return live
