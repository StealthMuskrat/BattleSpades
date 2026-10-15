# Pure Java ENet strategy

The installed repository vendors ENet 1.3 C source and pyenet. It is the test
oracle, not a Java production dependency. Initial discovery found JNI wrappers
(ykatyhoney/enet-java and theoplegends/java-enet-jni), which fail the pure Java
requirement. No existing Java candidate has been validated for current ENet
wire compatibility, maintenance, range coding and licensing. This is not a claim
that no such implementation exists.

Proceed with a direct, attributed port of the vendored ENet protocol, validating
against its original native implementation in development. Current components:
command envelope serialization, bounded fragment reassembly, modular sequences,
and an adaptive order-2 range coder with native C golden vectors, including
model resets and rescaling at longer input lengths.

Still required: full host/peer lifecycle, ACK scheduling, sequencing queues,
retransmission, reliable window/congestion/throttle behavior, fragmentation
send/receive ownership/expiry, unsequenced duplicate windows, session reuse,
loss/reorder/interruption/reconnect soak, application handshake/map transfer.
A UDP codec or successful range-coder golden test is not a functioning ENet host.
An ENet reference peer is not the original game client.

The standalone `ConnectionHandshake` was additionally exercised over real UDP
against the compiled vendored pyenet reference. The native peer emitted CONNECT
and Java received the VERIFY_CONNECT acknowledgement, with protocol 168, one
channel and range coding enabled. This proves only that phase. The test probe
exits immediately; it is deliberately not exposed as a gameplay host.
