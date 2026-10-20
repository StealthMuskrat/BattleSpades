"""Offline SQLite-to-portable achievement export. Original database is opened read-only."""
import argparse,json,os,pathlib,sqlite3,tempfile

def export(source,output):
 source=pathlib.Path(source).resolve();output=pathlib.Path(output).resolve()
 if output.exists():raise ValueError('Output already exists; export into a new file')
 with sqlite3.connect(source.as_uri()+'?mode=ro',uri=True) as db:
  db.execute('BEGIN')
  counters={};unlocks={}
  for identity,stat,value in db.execute('SELECT identity,stat,value FROM counters'):
   if value<0:raise ValueError('Negative legacy counter requires review')
   counters.setdefault(identity,{})[stat]=value
  for identity,name,at,player,reported in db.execute('SELECT identity,api_name,unlocked_at,player_name,reported_at FROM unlocks'):
   unlocks.setdefault(identity,{})[name]={'at':at,'name':player,'reported':reported}
  payload=json.dumps({'version':1,'counters':counters,'unlocks':unlocks},ensure_ascii=True,allow_nan=False,separators=(',',':'))+'\n'
 output.parent.mkdir(parents=True,exist_ok=True)
 # Exclusive creation prevents an accidental overwrite during cutover.
 with output.open('x',encoding='utf-8') as handle:handle.write(payload);handle.flush();os.fsync(handle.fileno())
 return sum(len(v) for v in counters.values()),sum(len(v) for v in unlocks.values())
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source',required=True);parser.add_argument('--output',required=True);args=parser.parse_args();counts=export(args.source,args.output);print(f'Exported {counts[0]} counters and {counts[1]} unlocks')
