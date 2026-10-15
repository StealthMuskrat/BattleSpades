"""Native pyenet <-> pure Java ENet host integration over an impaired real UDP relay.
Development only; neither Python nor native ENet is used by Java production code.
Run after ./gradlew integrationTestClasses, with JAVA_HOME pointing to a JDK25.
"""
import hashlib,heapq,json,os,pathlib,random,socket,struct,subprocess,sys,time
ROOT=pathlib.Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT));sys.path.insert(0,str(ROOT/'java/build/reference-python'));import enet

class Relay:
    def __init__(self,server,impaired):
        self.socket=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);self.socket.bind(('127.0.0.1',0));self.socket.setblocking(False)
        self.server=server;self.client=None;self.impaired=impaired;self.random=random.Random(168);self.queue=[];self.counter=0
        self.dropped=0;self.duplicates=0;self.delayed=0;self.forwarded=0;self.first_server=True;self.old_packet=None
    def pump(self):
        for _ in range(2048):
            try:data,address=self.socket.recvfrom(65535)
            except BlockingIOError:break
            from_server=address==self.server
            if not from_server:self.client=address
            target=self.client if from_server else self.server
            if target is None:continue
            if not from_server and self.old_packet is None and (struct.unpack('!H',data[:2])[0]&4095)!=4095:self.old_packet=data
            if self.impaired and ((from_server and self.first_server) or self.random.random()<0.09):
                self.dropped+=1
                if from_server:self.first_server=False
                continue
            if from_server:self.first_server=False
            delay=self.random.random()*0.025 if self.impaired else 0
            self.counter+=1;heapq.heappush(self.queue,(time.monotonic()+delay,self.counter,data,target))
            if delay:self.delayed+=1
            if self.impaired and self.counter%13==0:
                self.counter+=1;self.duplicates+=1;heapq.heappush(self.queue,(time.monotonic()+0.03,self.counter,data,target))
        now=time.monotonic()
        while self.queue and self.queue[0][0]<=now:
            _,_,data,target=heapq.heappop(self.queue);self.socket.sendto(data,target);self.forwarded+=1
    def close(self):self.socket.close()

def run(impaired):
    jdk=pathlib.Path(os.environ['JAVA_HOME']);classpath=os.pathsep.join(str(ROOT/'java/build/classes/java'/p) for p in ['main','integrationTest'])
    process=subprocess.Popen([str(jdk/'bin/java'),'-cp',classpath,'net.battlespades.TransportProbe','0'],cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    relay=None
    try:
        ready=process.stdout.readline().strip().split();assert ready[0]=='READY',ready
        relay=Relay(('127.0.0.1',int(ready[1])),impaired)
        received=[];events=[]
        def pump(host,condition,seconds=20):
            until=time.monotonic()+seconds
            while time.monotonic()<until:
                relay.pump()
                for _ in range(256):
                    event=host.service(0)
                    if event.type==enet.EVENT_TYPE_NONE:break
                    events.append(event)
                    if event.type==enet.EVENT_TYPE_RECEIVE:received.append(bytes(event.packet.data))
                relay.pump()
                if condition():return
                if process.poll() is not None:raise AssertionError(('Java exited',process.returncode,process.stdout.read(),process.stderr.read()))
                time.sleep(0.001)
            raise AssertionError(('native transport timeout',len(received),[(e.type,e.data) for e in events if e.type!=enet.EVENT_TYPE_RECEIVE]))
        def connect(protocol=168):
            host=enet.Host(None,peerCount=1,channelLimit=1,incomingBandwidth=0,outgoingBandwidth=0);host.compress_with_range_coder()
            peer=host.connect(enet.Address(b'127.0.0.1',relay.socket.getsockname()[1]),1,protocol)
            return host,peer
        rejections={}
        for protocol,reason in [(167,10),(169,3)]:
            host,peer=connect(protocol);pump(host,lambda:any(e.type==enet.EVENT_TYPE_DISCONNECT for e in events))
            assert not any(e.type==enet.EVENT_TYPE_CONNECT for e in events)
            assert [e.data for e in events if e.type==enet.EVENT_TYPE_DISCONNECT]==[reason]
            rejections[str(protocol)]=reason;host=None;events.clear();received.clear()
        host,peer=connect();pump(host,lambda:any(e.type==enet.EVENT_TYPE_CONNECT for e in events))
        expected=[];rng=random.Random(25)
        for n in range(80):
            size=[7,64,512,1373,9000,65536,131072][n%7] if n<14 else 64
            body=b'R'+struct.pack('!I',n)+bytes(rng.randrange(256) for _ in range(size-5));expected.append(body)
            peer.send(0,enet.Packet(body,enet.PACKET_FLAG_RELIABLE))
        host.flush();pump(host,lambda:sum(d[:1]==b'R' for d in received)==len(expected),40)
        actual=[d for d in received if d[:1]==b'R'];assert actual==expected,'reliable order/content/duplicate mismatch'
        # Unreliable traffic may be dropped by ENet's adaptive throttle or by the relay.
        before=len(received)
        for tag,flags,size in [(b'U',0,64),(b'S',enet.PACKET_FLAG_UNSEQUENCED,64),(b'F',enet.PACKET_FLAG_UNRELIABLE_FRAGMENT,9000)]:
            payloads={tag+struct.pack('!I',n)+bytes([n])* (size-5) for n in range(16)}
            for body in sorted(payloads):peer.send(0,enet.Packet(body,flags))
            host.flush();until=time.monotonic()+1.0
            pump(host,lambda:time.monotonic()>=until,3)
            subset=[d for d in received[before:] if d[:1]==tag]
            assert len(subset)==len(set(subset)),('duplicate delivery',tag)
            assert all(d in payloads for d in subset),('unreliable corruption',tag)
            if tag==b'U':assert [d[1:5] for d in subset]==sorted(d[1:5] for d in subset),'unreliable order reversed'
        unreliable_count=len(received)-before
        # Deliver a reliable request for server-initiated graceful shutdown, then reconnect on the same relay address.
        relay.impaired=False
        peer.send(0,enet.Packet(b'DISC',enet.PACKET_FLAG_RELIABLE));host.flush()
        pump(host,lambda:any(e.type==enet.EVENT_TYPE_DISCONNECT for e in events),10)
        reason=[e.data for e in events if e.type==enet.EVENT_TYPE_DISCONNECT][-1];assert reason==18,reason
        old_packet=relay.old_packet;host=None;events.clear();received.clear()
        host,peer=connect();pump(host,lambda:any(e.type==enet.EVENT_TYPE_CONNECT for e in events))
        # Old-generation datagrams must not enter the reconnected peer.
        if old_packet:relay.socket.sendto(old_packet,relay.server)
        marker=b'R'+struct.pack('!I',999)+b'reconnected'
        peer.send(0,enet.Packet(marker,enet.PACKET_FLAG_RELIABLE));host.flush();pump(host,lambda:marker in received)
        assert received==[marker],('stale-session delivery',received)
        peer.send(0,enet.Packet(b'STOP',enet.PACKET_FLAG_RELIABLE));host.flush()
        pump(host,lambda:process.poll() is not None,5)
        output,error=process.communicate(timeout=5);assert process.returncode==0,(output,error)
        line=[x for x in output.splitlines() if x.startswith('RESULT ')][-1];_,connections,packets,retransmissions,malformed=line.split()
        assert int(connections)==2,(line,output)
        if impaired:assert int(retransmissions)>0,line
        return dict(profile='loss-reorder-duplicate' if impaired else 'nominal',reliable_messages=len(expected)+1,reliable_payload_sha256=hashlib.sha256(b''.join(expected)).hexdigest(),
                    unreliable_echoes=unreliable_count,reconnect=True,protocol_rejections=rejections,server_disconnect_reason=reason,java_retransmissions=int(retransmissions),
                    forwarded_datagrams=relay.forwarded,dropped_datagrams=relay.dropped,duplicate_datagrams=relay.duplicates,delayed_datagrams=relay.delayed,
                    java_malformed_datagrams=int(malformed),game_client_test=False,result='PASS')
    finally:
        if relay:relay.close()
        if process.poll() is None:process.kill();process.wait()

if __name__=='__main__':
    reports=[]
    for impaired in [False,True]:
        report=run(impaired);reports.append(report);print(json.dumps(report),flush=True)
    (ROOT/'java/docs/native-transport-results.json').write_text(json.dumps({'oracle':'compiled vendored pyenet / ENet','profiles':reports},indent=2)+'\n')
