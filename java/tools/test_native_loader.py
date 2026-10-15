"""Original native ENet peer -> Java application loader and canonical join catch-up.
The scene and console controls are test fixtures; this is not an original game-client test.
"""
import hashlib,json,os,pathlib,queue,struct,subprocess,sys,threading,time,zlib
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'java/tools'))
from test_native_transport import Relay,enet

def unlzf(data):
    out=bytearray();pos=0
    while pos<len(data):
        c=data[pos];pos+=1
        if c<32:
            n=c+1;assert pos+n<=len(data);out+=data[pos:pos+n];pos+=n
        else:
            n=c>>5;offset=(c&31)<<8
            if n==7:n+=data[pos];pos+=1
            offset+=data[pos];pos+=1;start=len(out)-offset-1;assert start>=0
            for i in range(n+2):out.append(out[start+i])
        assert len(out)<64*1024*1024
    return bytes(out)

def run(impaired,private):
    classes=[ROOT/'java/build/classes/java'/p for p in ['main','testSupport','integrationTest']]+[ROOT/'java/build/resources/testSupport']
    java=pathlib.Path(os.environ['JAVA_HOME'])/'bin/java';secret='test-password' if private else ''
    process=subprocess.Popen([str(java),'-cp',os.pathsep.join(map(str,classes)),'net.battlespades.LoaderProbe',secret],cwd=ROOT,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    output=queue.Queue()
    def read_output():
        for line in process.stdout:output.put(line.strip())
    threading.Thread(target=read_output,daemon=True).start()
    clients=[];messages=[]
    try:
        ready=output.get(timeout=15).split();assert ready[0]=='READY',ready;server=('127.0.0.1',int(ready[1]))
        class Client:
            def __init__(self):
                self.relay=Relay(server,impaired);self.host=enet.Host(None,peerCount=1,channelLimit=1,incomingBandwidth=0,outgoingBandwidth=0);self.host.compress_with_range_coder()
                self.peer=self.host.connect(enet.Address(b'127.0.0.1',self.relay.socket.getsockname()[1]),1,168)
                self.connected=False;self.reason=None;self.key=b'';self.frames=[];self.last_input=0;self.input_enabled=False;self.player=None;self.input_count=0
                clients.append(self)
            def send(self,body,compressed=False,flags=enet.PACKET_FLAG_RELIABLE,encrypt=True):
                if encrypt and self.key:body=bytes(b^self.key[i%len(self.key)] for i,b in enumerate(body))
                if compressed:
                    data=b''.join(bytes([len(body[i:i+32])-1])+body[i:i+32] for i in range(0,len(body),32));wire=b'\x31'+data
                else:wire=b'\x30'+body
                self.peer.send(0,enet.Packet(wire,flags));self.host.flush()
            def count(self,ident):return sum(body[0]==ident for prefix,body in self.frames)
            def bodies(self,ident):return [body for prefix,body in self.frames if body[0]==ident]
        def pump(condition,seconds=30):
            deadline=time.monotonic()+seconds
            while time.monotonic()<deadline:
                if process.poll() is not None:raise AssertionError(('Java probe exited',process.returncode,process.stderr.read(),messages))
                for c in clients:
                    c.relay.pump()
                    for _ in range(256):
                        event=c.host.service(0)
                        if event.type==enet.EVENT_TYPE_NONE:break
                        if event.type==enet.EVENT_TYPE_CONNECT:c.connected=True
                        elif event.type==enet.EVENT_TYPE_DISCONNECT:c.reason=event.data
                        elif event.type==enet.EVENT_TYPE_RECEIVE:
                            wire=bytes(event.packet.data);assert wire[0] in [0x30,0x31,0x32];body=unlzf(wire[1:]);assert body
                            c.frames.append((wire[0],body))
                    if c.input_enabled and time.monotonic()-c.last_input>=0.015 and c.reason is None:
                        c.last_input=time.monotonic();c.input_count+=1
                        body=struct.pack('<BiBBHHHBBB',4,c.input_count,c.player,7,8192,0,0,0,0,0)+struct.pack('<f',0.0)
                        c.send(body,flags=enet.PACKET_FLAG_UNSEQUENCED)
                    c.relay.pump()
                while True:
                    try:messages.append(output.get_nowait())
                    except queue.Empty:break
                if condition():return
                time.sleep(0.001)
            raise AssertionError(('Loader timeout',[(c.connected,c.reason,[b[0] for _,b in c.frames[-12:]]) for c in clients],messages))
        def command(text,prefix):
            before=sum(m.startswith(prefix) for m in messages);process.stdin.write(text+'\n');process.stdin.flush();pump(lambda:sum(m.startswith(prefix) for m in messages)>before)
            return [m for m in messages if m.startswith(prefix)][-1]
        def ticket(c,cap):
            key=b'\x03\x07\x13\x80';c.send(b'\x69'+struct.pack('<i',len(key))+key+(b'BSCF'+bytes([cap]) if cap else b''));c.key=key
            if private:
                pump(lambda:c.count(112)==1);assert c.count(114)==0;c.send(b'\x71wrong\0');pump(lambda:c.count(112)==2)
                until=time.monotonic()+0.55;pump(lambda:time.monotonic()>=until,2);c.send(b'\x6ftest-password\0',compressed=True)
            pump(lambda:c.count(114)==1 or c.reason is not None)
        def initial_crc(body):
            assert body[0]==114;pos=17
            for _ in range(7):pos=body.index(0,pos)+1
            return struct.unpack_from('<I',body,pos)[0]
        def validate(c,crc,full):
            before=len(c.frames);c.send(b'\x3c'+struct.pack('<I',crc),compressed=True)
            pump(lambda:c.count(45)>0 or c.reason is not None);assert c.reason is None,c.reason
            frames=c.frames[before:];assert [b[0] for _,b in frames[:2]]==[60,55]
            actual_crc=struct.unpack_from('<I',frames[0][1],1)[0];assert actual_crc==zlib.crc32(b'\x00\xf0\xef\x00')
            start=[(p,b) for p,b in frames if b[0]==55];assert start==[(0x32,b'\x37')]
            chunks=[];percents=[]
            for prefix,body in frames:
                if body[0]==57:
                    percent,length=struct.unpack_from('<Bh',body,1);assert prefix==0x31 and length==len(body)-4 and 1<=length<=1024;chunks.append(body[4:]);percents.append(percent)
            assert percents==sorted(percents);assert any(b[0]==59 for _,b in frames)
            if full:
                records=zlib.decompress(b''.join(chunks));assert len(records)==512*512*16
                for index in range(512*512):
                    x,y=struct.unpack_from('<II',records,index*16);assert (x,y)==(index%512,index//512);assert records[index*16+8:index*16+16]==b'\x00\xef\xef\x00\x00\x00\x00\x00'
                digest=hashlib.sha256(records).hexdigest()
            else:assert chunks==[];digest=hashlib.sha256(b'').hexdigest()
            c.player=c.bodies(45)[-1][1];return actual_crc,len(chunks),digest
        first=Client();pump(lambda:first.connected);first.send(b'\x0f\x01\x05\x00\x01forged\0');assert command('STATS','STATS').split()[1]=='0'
        # Optional UGC tail is absent in this observed class-selection layout.
        first.send(b'\x0d\xff\x05\x01\x01\x07\x01one\0');ticket(first,2)
        assert first.bodies(114)[0].endswith(b'BSFP\x02'+struct.pack('<B9H',7,64,1920,576,480,1280,1280,1280,256,160))
        expected_crc=initial_crc(first.bodies(114)[0]);actual_crc,full_chunks,full_hash=validate(first,0,True);assert expected_crc==actual_crc;assert first.player==0
        second=Client();pump(lambda:second.connected);ticket(second,0);_,delta_chunks,_=validate(second,actual_crc,False);assert second.player==1
        third=Client();pump(lambda:third.connected);third.send(b'\x69\x00\x00\x00\x00')
        if private:
            pump(lambda:third.count(112)>0);third.send(b'\x71test-password\0')
        pump(lambda:third.reason is not None);assert third.reason==4 and third.count(114)==0
        for c,name in [(first,b'one'),(second,b'two')]:c.send(b'\x0f\x01\x05\x00\x01'+name+b'\0')
        pump(lambda:first.count(28)>0 and second.count(28)>0)
        command('EDIT 1 1 1 1 123456 9','APPLIED');command('EDIT 1 1 1 1 654321 none','APPLIED');command('EDIT 2 1 1 0 0 none','APPLIED')
        first.input_enabled=second.input_enabled=True
        pump(lambda:all(any(b'fixture-reveal' in b for b in c.bodies(49)) for c in [first,second]))
        pump(lambda:all(c.count(38)>=1 and c.count(37)>=1 for c in [first,second]))
        for attempt in range(40):
            stats=command('STATS','STATS')
            if stats.split()[1]=='2' and stats.count('LIVE')==2:break
            until=time.monotonic()+0.1;pump(lambda:time.monotonic()>=until,2)
        else:raise AssertionError(stats)
        expected_health=b'\x26'+struct.pack('<ii',0,1)+struct.pack('<hhhB',1,1,1,36)+struct.pack('<i',0)
        for c in [first,second]:
            assert expected_health in c.bodies(38);build=c.bodies(33)[-1];assert struct.unpack_from('<hhh',build,6)==(1,1,1) and build[-3:]==b'\x21\x43\x65'
            removal=c.bodies(37)[-1];assert removal[2:7]==bytes([6,127,0,0,0]);assert struct.unpack_from('<fff',removal,9)==(2.0,1.0,1.0)
            assert not c.bodies(14);assert {b[1] for b in c.bodies(28)}=={0,1}
        # Keep ENet and XOR keys over rollover. Stock clients need no custom packet110.
        for c in [first,second]:c.input_enabled=False
        before=[c.count(114) for c in [first,second]];command('RELOAD','RELOADED');pump(lambda:all(c.count(114)>n for c,n in zip([first,second],before)))
        for c in [first,second]:
            assert c.count(52)==1;c.frames=[];c.send(b'\x3c'+struct.pack('<I',actual_crc),compressed=True)
        pump(lambda:all(c.count(45)>0 for c in [first,second]));assert all(c.count(112)==0 and c.count(57)==0 and c.count(55)==1 and c.count(59)==1 for c in [first,second])
        process.stdin.write('STOP\n');process.stdin.flush();process.wait(timeout=5);assert process.returncode==0,process.stderr.read()
        return dict(profile='impaired' if impaired else 'nominal',private_password=private,full_chunks=full_chunks,full_records_sha256=full_hash,delta_chunks=delta_chunks,
                    dropped_datagrams=sum(c.relay.dropped for c in clients),duplicate_datagrams=sum(c.relay.duplicates for c in clients),delayed_datagrams=sum(c.relay.delayed for c in clients),concurrent_reserved_ids=[0,1],full_refusal_reason=third.reason,ticket_xor=True,capability_trailer=True,canonical_health=True,canonical_removal=True,roster_reconcile=True,scene_reload=True,game_client_test=False,result='PASS')
    finally:
        for c in clients:c.relay.close()
        if process.poll() is None:process.kill();process.wait()

if __name__=='__main__':
    results=[]
    for impaired,private in [(False,False),(True,True)]:
        result=run(impaired,private);results.append(result);print(json.dumps(result),flush=True)
    (ROOT/'java/docs/native-loader-results.json').write_text(json.dumps({'oracle':'vendored native ENet with independently decoded application packets','profiles':results},indent=2)+'\n')
