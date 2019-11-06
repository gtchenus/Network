import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/***************************************************************************
 * ClassName: Receiver
 * Date: 10/27/2019
 * Version: 1.0
 * Description: Receiver entity of modeling FSM in GBN TCP, which can buffer,
 * check, and Ack with the packets sent from sender. The receiver has a fixed
 * size buffer of selective Acks, which is used to reduce the number of
 * retransmission by sender in the GBN protocal.
 * Author: Guanting Chen
 * Date: 10/27/2019
 ****************************************************************************/

public class Receiver extends AbstractHost {
    private Packet[] rcvBuff;     // only for buffering packets with acks within (expectSeq, expectSeq + 5)
    private int buffSize;
    private int rcvdSeq;
    private int expectSeq;

    public Receiver() {
        super();
        this.id = 1;
        this.rcvdSeq = 0;
        this.expectSeq = 1;
        this.seqSpace = 10;
        this.rcvBuff = new Packet[5];
        this.buffSize = 0;
    }

    public Receiver(int buffCap, int seqSpace) {
        super();
        this.id = 1;
        this.rcvdSeq = 0;
        this.expectSeq = 1;
        this.seqSpace = seqSpace;
        this.buffSize = 0;
        this.rcvBuff = new Packet[buffCap];
    }


    private int shoudBuffer(int seq) {
        boolean flag = false;
        int index = 0;
        for (int i = expectSeq; index < getBufferCapacity(); i = nextSeq(i), ++index) {
            if (seq == i) {
                flag = true;
                break;
            }
        }
        return flag ? index : -1;
    }

    /**
     * Handling received non-corrupted packets by
     * 1. check whether the packet is exactly we want.
     * 2. if not the exact one, check whether we can buffer it
     *
     * @return true indicating
     * 1. pkt.seq can be toLayer above
     * 2. sacks continuously jointed with pkt.seq can be toLayer above as well
     * 3. rcvdSeq and expectSeq should be updated based on previous two
     */
    public boolean hasSeqnum(Packet pkt) {
        int seq = pkt.getSeqnum();
        // buffer packets starting with [expectPkt, ) first
        int index = shoudBuffer(seq);
        if (index >= 0) {
            this.rcvBuff[index] = pkt;
            ++this.buffSize;
        }
        if (seq == this.expectSeq) {
            return true;
        }
        return false;
    }

    /**
     * remove the first element from buffer
     * move every element forward and fill a null to the end
     *
     * @return not null first element
     */
    public Packet poll() {
        Packet firstPkt = this.rcvBuff[0];
        if (isBufferEmpty() || firstPkt == null) {
            throw new RuntimeException("Polling from receiver should never happen without a not-null fist element");
        }
        for (int i = 0; i < rcvBuff.length; ++i) {
            if (i == rcvBuff.length - 1) {
                rcvBuff[i] = null;
                break;
            }
            rcvBuff[i] = rcvBuff[i + 1];
        }
        --this.buffSize;
        return firstPkt;
    }

    public int[] getSacks() {
        int capacity = this.getBufferCapacity();
        int[] sacks = new int[capacity];
        Packet pkt = null;
        for (int i = 0; i < capacity; ++i) {
            pkt = this.rcvBuff[i];
            sacks[i] = (pkt != null) ? pkt.getSeqnum() : 0;
        }
        return sacks;
    }

    public Packet ackPacket() {
        int seq = 0;        // useless
        int ack = getCumuAck();
        int[] sack = getSacks();
        Message msg = new Message(null);
        int checksum = ChecksumUtil.getCheckSum(seq, ack, msg, sack);
        return new Packet(seq, ack, checksum, msg.getData(), sack);
    }

    public boolean isBufferEmpty() {
        return this.buffSize == 0;
    }

    public void resetBuffer() {
        Arrays.fill(rcvBuff, null);
        this.buffSize = 0;
    }

    public int getCumuAck() {
        return this.rcvdSeq;
    }

    public void addCumuAck() {
        this.rcvdSeq = nextSeq(this.rcvdSeq);
    }

    public void addExpectSeq() {
        this.expectSeq = nextSeq(this.rcvdSeq);
    }

    public int getBufferCapacity() {
        return this.rcvBuff.length;
    }

    public int getExpectSeq() {
        return this.expectSeq;
    }

    /**
     * Iterator through continuous stored packets
     * in a point in time snapshot of rcvBuff
     * e.g. {pkt(3),pkt(4), null, pkt(6)} ==> pkt(3) and pkt(4)
     */
    public Iterator<Packet> iterator() {
        return new RcvBuffIterator();
    }

    private class RcvBuffIterator implements Iterator<Packet> {
        private int curIndex;
        private Packet[] snapshot;

        RcvBuffIterator() {
            this.curIndex = 0;
            this.snapshot = new Packet[rcvBuff.length];
            System.arraycopy(rcvBuff, 0, snapshot, 0, rcvBuff.length);
        }

        @Override
        public boolean hasNext() {
            if (curIndex < snapshot.length &&
                    snapshot[curIndex] != null) return true;
            return false;
        }

        @Override
        public Packet next() {
            Packet pkt = snapshot[curIndex];
            if (pkt == null) throw new NoSuchElementException();
            ++this.curIndex;
            return pkt;
        }
    }
}
