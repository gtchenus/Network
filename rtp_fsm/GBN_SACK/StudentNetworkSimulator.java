import java.util.*;

public class StudentNetworkSimulator extends NetworkSimulator {
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                     Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity):
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment):
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     *  Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData):
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *          returns the Packet's payload
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;

    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)
    // custom code starts here
    private Sender snder;
    private Receiver rcver;
    // Statistics
    private int numOTN;             // Number of original packets transmitted by A
    private int numRTN;             // Number of retransmissions by A
    private int numToLayer5B;       // Number of data packets delivered to layer 5 at B
    private int numACK;             // Number of ACK packets sent by B
    private int numCorrupt;         // Number of corrupted packets
    private double ratioLost;       // Ratio of lost packets
    private double ratioCorrupt;    // Ratio of corrupted packets
    private double avgRTT;          // Average RTT
    private double cumuCOMM;        // Cumulative communication time
    private int commCount;          // Cumulative communication count
    private double avgCOMM;         // Average communication time



    // This is the constructor.  Don't touch!
    public StudentNetworkSimulator(int numMessages,
                                   double loss,
                                   double corrupt,
                                   double avgDelay,
                                   int trace,
                                   int seed,
                                   int winsize,
                                   double delay) {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize * 2; // set appropriately; assumes SR here!
        RxmtInterval = delay;
    }


    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message) {
        if (!snder.output(message)) {
            System.out.println("Send buffer is full, drop incoming message");
            return;
//            throw new RuntimeException("Buffer is full");
        }

        // be able to send a window
        // start a timer
        Iterator<Integer> it = snder.sndWindowUnsentIterator();
//        stopTimer(snder.id());
        startTimer(snder.id(),RxmtInterval);
        while (it.hasNext()) {
            Packet pkt = snder.get(it.next());
            snder.rtt.addSent(getTime());
            toLayer3(snder.id, pkt);
            ++this.numOTN;
            System.out.println("Sender: sent a packet: " + pkt.toString());
            // window.next + 1
            snder.updateNextToSend();
        }
    }

    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet) {
        System.out.println("Sender: received: " + packet.toString());
        double rcvdTime = getTime();
        // check corruption, drop if corrupt
        if (ChecksumUtil.isCorrupted(packet)) {
            ++this.numCorrupt;
            return;
        }
        // check whether the ACK is the expecting cumulative ACK
        boolean isExpectSeq = snder.hasSeqnum(packet);
        // check whether packet has any qualified ACKs(Belongs to send window)
        // If has, try signaling and updating it on BitMap
        Set<Integer> indexes= snder.updateBitMap(packet);
        if (indexes.size() > 0) {
            // has some Bitmap updates
            snder.rtt.addRcvdCOMM(rcvdTime, indexes);
        } else ;// bitMap already update to date

        if (isExpectSeq) {
            // received a qualified ACK
            // stop and start timer
            System.out.println("Sender: PACKET QUALIFIED >> PASS TOLAYER5 AND SLIDE SEND WINDOW");
            snder.rtt.addRcvdRTT(rcvdTime, packet.getAcknum());
            stopTimer(snder.id());
            // checking bitmap to update base and slide right the window
            List<Packet> packetList = snder.slideWindow();
            for (Packet pkt : packetList) {
                toLayer5(pkt.getPayload());
            }
        } else {
            // Duplicate ACK, retransmit the first unACK’ed data packet
            System.out.println("Sender: packet has DUPLICATED ACK");
            LinkedList<Packet> packetList = snder.retransmit();
            stopTimer(snder.id());
            startTimer(snder.id(), RxmtInterval);
            if (!packetList.isEmpty()) {
                System.out.println("Sender: RETRANSMIT due to DUPLICATED ACK: " + packetList.toString());
                toLayer3(snder.id(), packetList.peek());
                ++this.numRTN;
                snder.rtt.setToRetransmission();
            }

        }
    }

    // This routine will be called when A's timer expires (thus generating a 
    // timer interrupt). You'll probably want to use this routine to control 
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped. 
    protected void aTimerInterrupt() {
        System.out.println("Sender: TIMEOUT");
        List<Packet> packetList = snder.retransmit();
        if (!packetList.isEmpty()) {
            stopTimer(snder.id());
            startTimer(snder.id(), RxmtInterval);
            System.out.println("Sender: RETRANSMIT due to TIMEOUT: " + packetList.toString());
            for (Packet pkt : packetList) {
                toLayer3(snder.id(), pkt);
                ++this.numRTN;
            }
            snder.rtt.setToRetransmission();
        }

    }

    // This routine will be called once, before any of your other A-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit() {
        this.snder = new Sender(50, this.WindowSize, this.LimitSeqNo);
    }

    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet) {
//        System.out.println("Receiver: received: " + packet.toString());
        // Check corruption, drop and NACK if corrupted
        boolean corrupted = false;
        if (ChecksumUtil.isCorrupted(packet)) {
            ++numCorrupt;
            corrupted = true;
        }
        // If received an exactly expected packet
        // Pass payloads to Layer 5 of expected packet and buffered packets

        if (!corrupted  && rcver.hasSeqnum(packet)) {
            System.out.println("Receiver: PACKET QUALIFIED >> PASS TOLAYER5 and UPDATE CUMULATIVE ACK");
            Iterator<Packet> it = rcver.iterator();
            while (it.hasNext()) {
                toLayer5(it.next().getPayload());
                ++this.numToLayer5B;
                rcver.poll();
                rcver.addCumuAck();
                rcver.addExpectSeq();
            }
            // Ack back for qualified packet with updated cumu ack and sack
            Packet ackPacket = rcver.ackPacket();
            toLayer3(rcver.id(), ackPacket);
            ++this.numACK;
            System.out.println("Receiver: ACK: " + ackPacket.toString());
            return;
        }
        // Ack back for unqualified packets
        Packet ackPacket = rcver.ackPacket();
        toLayer3(rcver.id(), ackPacket);
        ++this.numACK;
         System.out.println("Receiver: NACK: " + ackPacket.toString());

    }

    // This routine will be called once, before any of your other B-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit() {
        this.rcver = new Receiver(5, this.LimitSeqNo);
    }

    // Use to print final statistics
    protected void Simulation_done() {
        this.ratioLost = (double)(numRTN - numCorrupt) / (numOTN+numRTN+numACK);
        this.ratioCorrupt = (double)(numCorrupt) / ((numOTN + numRTN) + numACK - (numRTN - numCorrupt));
        this.avgRTT = snder.rtt.getCumulativeRTT() / snder.rtt.getRttCount();
        this.avgCOMM = snder.rtt.getCumulativeCOMM() / snder.rtt.getCommCount();


        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A: " + numOTN);
        System.out.println("Number of retransmissions by A: " + numRTN);
        System.out.println("Number of data packets delivered to layer 5 at B: " + numToLayer5B);
        System.out.println("Number of ACK packets sent by B: " + numACK);
        System.out.println("Number of corrupted packets: " + numCorrupt);
        System.out.printf("Ratio of lost packets: %.2f \n", ratioLost);
        System.out.printf("Ratio of corrupted packets: %.2f \n", ratioCorrupt);
        System.out.printf("Average RTT: %.2f \n", avgRTT);
        System.out.printf("Average communication time: %.2f \n", avgCOMM);
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        // EXAMPLE GIVEN BELOW
        System.out.println("Cumulative valid RTT = " + snder.rtt.getCumulativeRTT());
        System.out.println("RTT valid count = " + snder.rtt.getRttCount());
        System.out.println("Cumulative communication time = " + snder.rtt.getCumulativeCOMM());
        System.out.println("Communication valid count = " + snder.rtt.getCommCount());
        System.out.println("Number of qualified ACKs and SACKs (BitMap flips count) = " + snder.numBitMapUpdates());
        //System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>");
    }



}
