import java.util.Arrays;

public class Packet {
    private int seqnum;
    private int acknum;
    private int checksum;
    private String payload;
    private int sack[];     // ack latest 5 data packets received successfully

    public Packet(Packet p) {
        seqnum = p.getSeqnum();
        acknum = p.getAcknum();
        checksum = p.getChecksum();
        payload = new String(p.getPayload());
        sack = p.getSack();
    }

//    public Packet(int seq, int ack, int check, String newPayload) {
//        seqnum = seq;
//        acknum = ack;
//        checksum = check;
//        if (newPayload == null) {
//            payload = "";
//        } else if (newPayload.length() > NetworkSimulator.MAXDATASIZE) {
//            payload = null;
//        } else {
//            payload = new String(newPayload);
//        }
//        this.sack = new int[5];
//    }

    public Packet(int seq, int ack, int check, String newPayload, int[] sack) {
        seqnum = seq;
        acknum = ack;
        checksum = check;
        if (newPayload == null) {
            payload = "";
        } else if (newPayload.length() > NetworkSimulator.MAXDATASIZE) {
            payload = null;
        } else {
            payload = new String(newPayload);
        }
        this.sack = sack;
    }

    public Packet(int seq, int ack, int check) {
        seqnum = seq;
        acknum = ack;
        checksum = check;
        payload = "";
        this.sack = new int[5];
    }


    public boolean setSeqnum(int n) {
        seqnum = n;
        return true;
    }

    public boolean setAcknum(int n) {
        acknum = n;
        return true;
    }

    public boolean setChecksum(int n) {
        checksum = n;
        return true;
    }

    public boolean setPayload(String newPayload) {
        if (newPayload == null) {
            payload = "";
            return false;
        } else if (newPayload.length() > NetworkSimulator.MAXDATASIZE) {
            payload = "";
            return false;
        } else {
            payload = new String(newPayload);
            return true;
        }
    }

    public boolean setSack(int index, int ack) {
        if (index < 0 || index >= sack.length) {
            return false;
        }
        this.sack[index] = ack;
        return true;
    }

    public boolean setSack(int[] newSack) {
        if (newSack.length != sack.length) return false;
        this.sack = newSack;
        return true;
    }

    public int getSeqnum() {
        return seqnum;
    }

    public int getAcknum() {
        return acknum;
    }

    public int getChecksum() {
        return checksum;
    }

    public String getPayload() {
        return payload;
    }

    public int[] getSack() {
        return this.sack;
    }

    public int getSack(int index) {
        if (index < 0 || index >= sack.length) {
            throw new IllegalArgumentException("Invalid sack index");
        }
        return this.sack[index];
    }

    public String toString() {
        return ("seqnum: " + seqnum + "  acknum: " + acknum + "  checksum: " +
                checksum + "  payload: " + payload + "sack: " + Arrays.toString(this.sack));
    }

}
