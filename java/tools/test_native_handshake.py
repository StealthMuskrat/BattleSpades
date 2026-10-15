"""Development integration: compiled native pyenet connects to Java handshake probe.
Requires JDK25 and built Java integrationTest classes. Does not test the game client.
"""
import json,os,pathlib,socket,subprocess,sys,time
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT));import enet
jdk=pathlib.Path(os.environ['JAVA_HOME']);environment=os.environ.copy()
# Pick a free loopback port, then let Java bind it. This is a test-only race.
s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);s.bind(('127.0.0.1',0));port=s.getsockname()[1];s.close()
classpath=os.pathsep.join(str(ROOT/'java/build/classes/java'/p) for p in ['main','integrationTest'])
process=subprocess.Popen([str(jdk/'bin/java'),'-cp',classpath,'net.battlespades.HandshakeProbe',str(port)],cwd=ROOT,env=environment,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
try:
 ready=process.stdout.readline().strip();assert ready=='READY',ready
 host=enet.Host(None,peerCount=1,channelLimit=1,incomingBandwidth=0,outgoingBandwidth=0);host.compress_with_range_coder()
 peer=host.connect(enet.Address(b'127.0.0.1',port),1,168)
 deadline=time.monotonic()+8;connected=False
 while time.monotonic()<deadline:
  event=host.service(50)
  if event.type==enet.EVENT_TYPE_CONNECT: connected=True;host.flush();break
 assert connected,'native ENet never emitted CONNECT'
 output,error=process.communicate(timeout=5)
 assert process.returncode==0,(process.returncode,output,error)
 assert 'CONNECTED_ACKNOWLEDGED' in output,output
 report=dict(native_enet_connect_event=True,java_received_verify_ack=True,protocol=168,channel_count=1,range_coder_enabled=True,game_client_test=False,result='PASS')
 (ROOT/'java/docs/native-handshake-result.json').write_text(json.dumps(report,indent=2)+'\n')
 print(json.dumps(report))
finally:
 if process.poll() is None:process.kill();process.wait()
