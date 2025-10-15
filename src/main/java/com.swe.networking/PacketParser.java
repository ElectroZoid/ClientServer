package com.swe.networking;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/* Parser for the packets.
The structure of the packet is given below
- Type              : 2bits
- Priority          : 3bits
- Module            : 4bits
- Connection Type   : 3bits
- Broadcast         : 1bit
- empty             : 3bits ( for future use )
- IPv4 addr         : 32bits
- port num          : 16bits
- Message Id        : 32bits
- Chunk Num         : 32bits
- Chunk Length      : 32bits
- Payload           : variable length


0                             1
0  1  2  3  4  5  6  7  8  9  0  1  2  3  4  5  6
+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
|Type |Priority|   Module  |Con Type|BC|  empty |
+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
|                  IPv4 Address                 |
|                                               |
+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
|                 port number                   |
+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
|                  Message Id                   |
|                                               |
+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
|                  Chunk Number                 |
|                                               |
+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
|                  Chunk Length                 |
|                                               |
+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
| Payload....
+--+--+--+--+--+
*/


public class PacketParser {
    private static PacketParser parser = null;
    private static final int HEADER_SIZE = 8;

    private PacketParser() {}

    public static PacketParser getPacketParser() {
        if (parser == null) parser = new PacketParser();
        return parser;
    }

    public int getType( byte[] pkt ) {
        return ( pkt[0] >> 6 ) & 0b11;
    }


    public int getPriority( byte[] pkt ) {
        return (pkt[0] >> 3) & 0b111;
    }


    public int getModule( byte[] pkt ) {
        return ( ( pkt[0] & 0b111 ) << 1 ) | ( ( pkt[1] >> 7 ) & 0b1 );
    }


    public int getConnectionType( byte[] pkt ){
        return ( pkt[1] >> 4 ) & 0b111;
    }


    public int getBroadcast( byte[] pkt ){
        return ( pkt[1] >> 3 ) & 0b1;
    }

    public InetAddress getIpAddress(byte[] pkt) throws UnknownHostException {
        byte[] ipBytes = Arrays.copyOfRange(pkt, 2, 6);
        return InetAddress.getByAddress(ipBytes);
    }

    public int getPortNum(byte[] pkt) {
        ByteBuffer bb = ByteBuffer.wrap(pkt, 6, 2);
        return Short.toUnsignedInt(bb.getShort());
    }

    public int getMessageId(byte[] pkt) {
        ByteBuffer bb = ByteBuffer.wrap(pkt, 8, 4);
        return bb.getInt();
    }

    public int getChunkNum(byte[] pkt) {
        ByteBuffer bb = ByteBuffer.wrap(pkt, 12, 4);
        return bb.getInt();
    }

    public int getChunkLength(byte[] pkt) {
        ByteBuffer bb = ByteBuffer.wrap(pkt, 16, 4);
        return bb.getInt();
    }

    public byte[] getPayload(byte[] pkt) {
        return Arrays.copyOfRange(pkt, HEADER_SIZE, pkt.length);
    }

    public byte[] createPkt(int type, int priority, int module,
                            int connectionType, int broadcast,
                            InetAddress ipAddr, int portNum,
                            int messageId, int chunkNum,
                            int chunkLength, byte[] data) {

        byte[] pkt = new byte[HEADER_SIZE + data.length];
        ByteBuffer bb = ByteBuffer.wrap(pkt);

        // Byte 0 = Type(2) + Priority(3) + Module
        bb.put((byte) (
                ((type & 0b11) << 6) |
                        ((priority & 0b111) << 3) |
                        ((module & 0b1110) >> 1)
        ));

        // Byte 1 = Module  + ConnType(3) + Broadcast(1) + Empty(3)
        bb.put((byte) (
                ((module & 0b1) << 7) |
                        ((connectionType & 0b111) << 4) |
                        ((broadcast & 0b1) << 3)
        ));

        // Bytes 2–5
        bb.put(ipAddr.getAddress());

        // Bytes 6–7
        bb.putShort((short) portNum);

        // Bytes 8–11
        bb.putInt(messageId);

        // Bytes 12–15
        bb.putInt(chunkNum);

        // Bytes 16–19
        bb.putInt(chunkLength);

        // Bytes 20+
        bb.put(data);

        return pkt;
    }
}