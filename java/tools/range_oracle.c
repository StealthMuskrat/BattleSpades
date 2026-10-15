/* Development-only oracle adapter. No native code is linked into the Java application.
 * ENet is MIT; original notices at repository root. */
#include <stdlib.h>
#include "enet/enet.h"
void *enet_malloc(size_t size) { return malloc(size); }
void enet_free(void *data) { free(data); }
void enet_host_compress(ENetHost *host, const ENetCompressor *compressor) { (void)host; (void)compressor; }
