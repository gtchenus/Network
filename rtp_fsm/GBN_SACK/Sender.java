import java.util.*;

/***************************************************************************
 * ClassName: Sender
 * Date: 10/27/2019
 * Version: 1.0
 * Description: Sender entity of modeling FSM in GBN TCP, which can buffer,
 * send, and validate packets received. Sender maintains a send buffer, a
 * send window, a bitMap, and a seqnum allocated for the latest packet to
 * send. Both send window and bitMap are attached on send buffer and should
 * be updated with send buffer together when resizing our buffer. The bitMap
 * is only visible inside the send window.
 * Author: Guanting Chen
 * Date: 10/27/2019
 ****************************************************************************/

public class Sender extends AbstractHost {

    public RTTtimer rtt;        // for computing effective RTT
    private Buffer<Packet> sndBuff;
    private SendWindow sndWindow;
    private boolean[] bitMap;   // only for sender to reduce retransmission
    private int usedSeq;        // seq of the latest packet in buffer
    private int flipCount;      // number of bits flip count in bitMap


    public Sender() {
        super();
        this.id = 0;
        this.sndBuff = new Buffer<Packet>();        // default 50
        this.rtt = new RTTtimer(sndBuff.getCapacity());
        this.sndWindow = new SendWindow(10, sndBuff.getCapacity());
        this.bitMap = new boolean[sndBuff.getCapacity()];
        this.flipCount = 0;
        this.seqSpace = 2 * this.sndWindow.size();
    }

    public Sender(int buffCapacity, int windowSize, int seqSpace) {
        super();
        if (buffCapacity < windowSize) {
            throw new IllegalArgumentException("Sender window size must <= buffer capacity");
        }
        this.id = 0;
        this.sndBuff = new Buffer<Packet>(buffCapacity);
        this.rtt = new RTTtimer(buffCapacity);
        this.sndWindow = new SendWindow(windowSize, buffCapacity);
        this.bitMap = new boolean[50];
        this.flipCount = 0;
        this.seqSpace = seqSpace;
    }

    /**
     * Construct a packet for a incoming message
     * Buffer the packet and update Seqnum
     *
     * @return false if buffer is already full and can not be resized
     */
    public boolean output(Message msg) {
        // encap a packet
        int newSeq = nextSeq(this.usedSeq);
        int ack = 0;
        int[] sack = new int[5];
        int checksum = ChecksumUtil.getCheckSum(newSeq, ack, msg, sack);
        Packet pkt = new Packet(newSeq, ack, checksum, msg.getData(), sack);
        // check whether needs to resize
        if (this.sndBuff.isFull()) this.resizeAndUpdate();
        // put packet into buffer
        // update usedSeq, and buffSize in sndWindow
        if (sndBuff.add(pkt)) {
            this.usedSeq = newSeq;
            this.sndWindow.setBuffSize(sndBuff.size());
            return true;
        }

        return false;
    }

    /**
     * Retrieve and check an non-corrupted incoming packet
     * Set bitmap when packet falling into window range
     *
     * @param pkt incoming packet that
     *            1. Packet might be corrupted
     *            2. ACK might be out of snwWindow(received before)
     * @return true when Ack(packet) is inside window and Ack(packet) >= Seq(base), indicating
     * 1. Caller should slide sndWindow
     * 2. Caller should reset timer
     * @retrun false when
     * i. No Acknums in the range, do nothing
     */
    @Override
    public boolean hasSeqnum(Packet pkt) {
        // check packet ack and sacks in the range
        int ackIndex = getAckIndex(pkt.getAcknum());
        if (ackIndex >= 0) {
            // qualified packet inside sndWindow
            return true;
        }
        return false;
    }

    /*
     * Set the bit of ackIndex on bitMap
     * @return false when the corresponding bit has already been set
     */
    private boolean setBitMap(int ackIndex) {
        if (!this.sndWindow.contains(ackIndex)) {
            throw new IllegalArgumentException("Send window does not contain the index to set the bitMap");
        }
        return !setBitMapInternal(ackIndex, ackIndex).isEmpty();
    }

    public int numBitMapUpdates() {
        return this.flipCount;
    }

    /**
     * Signal cumulative ack on bitMap by setting true from [baseIndex, ackIndex]
     *
     * @param baseIndex starting position,
     *                  = windowBase when received a qualified packet with expecting ack
     *                  = ackIndex it self when received a qualified sack in the range of window
     * @param ackIndex  ending position
     *                  return a list of indexes in send window that has successfully been updated(flipped to 1)
     */
    private List<Integer> setBitMapInternal(int baseIndex, int ackIndex) {
        if (baseIndex > ackIndex) {
            throw new IllegalArgumentException();
        }
        List<Integer> updates = new LinkedList<>();
        for (int i = baseIndex; i <= ackIndex && !bitMap[i]; ++i) {
            bitMap[i] = true;
            ++this.flipCount;
            updates.add(i);
        }
        return updates;
    }


    /**
     * Sender API to update bitMap
     * Return a set of index being updated successfully
     */
    public Set<Integer> updateBitMap(Packet pkt) {
        Set<Integer> updatedSet = new LinkedHashSet<>();
        int ackIndex = getAckIndex(pkt.getAcknum());
        if (ackIndex >= 0) {
            // cumulative ack
            // qualified packet inside sndWindow
            List<Integer> updates = setBitMapInternal(this.sndWindow.getBase(), ackIndex);
            if (!updates.isEmpty()) updatedSet.addAll(updates);
        }
        int[] sacks = pkt.getSack();
        for (int i = 0; i < sacks.length && sacks[i] != 0; ++i) {
            ackIndex = getAckIndex(sacks[i]);
            if (ackIndex >= 0) {
                // has qualified sack inside sndWindow
                boolean isUpdated = setBitMap(ackIndex);
                if (!isUpdated) updatedSet.add(ackIndex);
            }
        }
        return updatedSet;
    }

    /**
     * Check and get the index in current window for a received ack
     *
     * @param ack must be >= 1
     * @return -1 if index for the seq is not exist in current window
     */
    private int getAckIndex(int ack) {
        if (ack < 1) return -1;
        if (sndWindow.isEmpty()) return -1;
        int returnIndex = -1;
        int baseSeq = getBaseSeq();
        int range = sndWindow.getNext() - sndWindow.getBase();
        int endSeq = ((baseSeq + range) == seqSpace)    // exclusive
                ? seqSpace : (baseSeq + range) % seqSpace;
        // seq should be within [baseSeq, baseSeq + range)
        if (endSeq > baseSeq) {
            if (ack >= baseSeq && ack < endSeq) returnIndex = sndWindow.getBase() + ack - baseSeq;
        } else if (endSeq < baseSeq) {
            if (ack >= baseSeq && ack <= seqSpace) returnIndex = sndWindow.getBase() + ack - baseSeq;
            if (ack < endSeq) returnIndex = sndWindow.getBase() + range - endSeq + ack;
        } else {
            // endSeq = baseSeq, send window is empty
            // encounter duplicate or unknown ack
            // drop it
//            throw new RuntimeException("some thing wrong");
        }
        return returnIndex;
    }

    public LinkedList<Packet> retransmit() {
        LinkedList<Packet> packetList = new LinkedList<>();
        Iterator<Integer> it = sndWindowSentIterator();
        while (it.hasNext()) {
            int index = it.next();
            if (!bitMap[index]) {
                packetList.add(sndBuff.get(index));
            }
        }
        return packetList;
    }

    public Packet get(int index) {
        return this.sndBuff.get(index);
    }

    public boolean isWindowEmpty() {
        return sndWindow.isEmpty();
    }

    /**
     * Move the index of nextToSend to the right
     * Triggered after we sent a packet
     * success if next has not reach the end of buffer
     */
    public void updateNextToSend() {
        int nextIndex = this.sndWindow.getNext();
        int boundary = this.sndBuff.size();
        // if already at the end of buffer
        if (nextIndex >= boundary)
            throw new RuntimeException("nextIndex can not move to the right because it is already at the end of buffer");
        this.sndWindow.setNext(nextIndex + 1);
    }


    public Iterator<Packet> sndBufferIterator() {
        return this.sndBuff.iterator();
    }

    /**
     * Triggered when window base cumulative ack(base) needs to update
     * Checking bitmap to slide window
     *
     * @return a list of packets to pass to the above layer, can be empty
     */
    public List<Packet> slideWindow() {
        int newBase = sndWindow.getBase();
        List<Packet> packets = new LinkedList<>();
        Iterator<Integer> it = sndWindowSentIterator();
        while (it.hasNext()) {
            int index = it.next();
            if (!bitMap[index]) break;
            packets.add(sndBuff.get(index));
            newBase += 1;

        }
        sndWindow.setBase(newBase);
        return packets;
    }

    /**
     * Resize both sndbuffer and bitmap when buffer is full
     *
     * @return false if still resizing fails
     */
    private void resizeAndUpdate() {
        int startIndex = this.sndWindow.getBase();
        int nextIndex = this.sndWindow.getNext();
        int capacity = this.sndBuff.getCapacity();
        boolean[] newBitMap = new boolean[capacity];
        int buffSize = this.sndBuff.resize(startIndex);
        // Always update sndWindnow and bitMap
        // because buffer reorganized is always triggered
        // whenever it is success or failed
        System.arraycopy(this.bitMap, startIndex, newBitMap, 0, nextIndex - startIndex);
        this.bitMap = newBitMap;
        this.rtt.resize(startIndex, nextIndex);
        this.sndWindow.reset(buffSize, 0, nextIndex - startIndex);
    }


    private int getBaseSeq() {
        return sndBuff.get(sndWindow.getBase()).getSeqnum();
    }

    public Iterator<Integer> sndWindowWholeIterator() {
        return this.sndWindow.iterator();
    }

    public Iterator<Integer> sndWindowUnsentIterator() {
        int start = this.sndWindow.getNext();
        return this.sndWindow.iterator(start);
    }

    public Iterator<Integer> sndWindowSentIterator() {
        int start = this.sndWindow.getBase();
        int end = this.sndWindow.getNext();
        return this.sndWindow.iterator(start, end);
    }

    @Deprecated
    private void setNextIndex(int index) {
        int buffCap = this.sndBuff.getCapacity();
        if (index > buffCap) index = buffCap;
        this.sndWindow.setNext(index);
    }

    /**
     * Get the expect cumulative ack by checking bitmap
     */
    @Deprecated
    public int ackToExpect() {
        int baseSeq = getBaseSeq();
        int range = 0;
        for (int i = sndWindow.getBase(); i < sndWindow.getNext() && bitMap[i]; ++i) {
            ++range;
        }
        return (baseSeq + range == this.seqSpace) ? seqSpace : (baseSeq + range) % seqSpace;
    }

    /**
     * RTTtimer is a helper class for computing effective
     * RTT. Effective rtt is calculate based on a send time buffer
     * and when receiving a in-window packet.
     */
    public class RTTtimer {
        private Buffer<Double> rttBuff;     // sent times of every packet in current sndWindow
        private Buffer<Double> commBuff;    // same as rttBuff but for calculating
        private double cumuRTT;             // RTT time for only received hasSeqnum ACK
        private double cumuCOMM;            // communication time for every received non-dup ACK
        private int rttCount;
        private int commCount;

        public RTTtimer(int capacity) {
            this.rttBuff = new Buffer<>(capacity);
            this.commBuff = new Buffer<>(capacity);
            this.cumuRTT = 0;
            this.rttCount = 0;
            this.cumuCOMM = 0;
            this.commCount = 0;
        }

        /**
         * Only for RTT buffer, When having a retransmission,
         * signal the range of sent of send window in sendTime buffer
         */
        public void setToRetransmission() {
            int start = sndWindow.getBase();
            int end = sndWindow.getNext();
            while (start < end) {
                this.rttBuff.set(start, -1.0);
                ++start;
            }
        }

        /**
         * add a sent time into both both RTT and COMM buffer
         *
         * @param time sent time of a packet in send window
         */
        public boolean addSent(double time) {
            return this.rttBuff.add(time) && this.commBuff.add(time);
        }

        /**
         * Add a received packet in to RTT timer and
         * calculate its RTT, contributing to cumuRTT if qualified
         * Qualified condition:
         * 1. Ack is not a subsequently ending ack(receiver buffer effect)
         * from receiver (should be the same first ack as the window base)
         * 2. Packet is not sent by retransmission
         *
         * @param time pkt received time at the sender
         * @param ack  pkt ack to identify whether it is in sndWidnow
         * @return true if received contributes to RTT
         */
        public boolean addRcvdRTT(double time, int ack) {
            int index = getAckIndex(ack);
            double sntTime = 0;
            double singleRtt = 0;

            if (index >= 0 && index == sndWindow.getBase()) {
                sntTime = rttBuff.get(index);
                if (sntTime > 0) {
                    singleRtt = time - sntTime;
                    this.cumuRTT += singleRtt;
                    this.rttCount += 1;
                    return true;
                }
            }
            return false;
        }

        /**
         * For every received and qualified packet, including packets
         * received by retransmission, calculate its communication time
         */
        public void addRcvdCOMM(double time, Set<Integer> indexes) {
            double sntTime = 0;
            for (Integer index : indexes) {
                if (sndWindow.contains(index)) {
                    sntTime = commBuff.get(index);
                    this.cumuCOMM += time - sntTime;
                    this.commCount += 1;
                }
            }
        }

        public double getCumulativeRTT() {
            return cumuRTT;
        }

        public int getRttCount() {
            return rttCount;
        }

        public double getCumulativeCOMM() {
            return cumuCOMM;
        }

        public int getCommCount() {
            return commCount;
        }

        /**
         * Only used the resizing function of outer class!!
         * Timer buffer should have the same resizing effects as bitMap,
         * rather than send buffer, should be resized before resetting
         * send window
         */
        private void resize(int start, int end) {
            this.rttBuff.truncate(start, end);
            this.commBuff.truncate(start, end);
        }
    }

}
