/***************************************************************************
 * ClassName: AbstractHost
 * Date: 10/27/2019
 * Version: 1.0
 * Description: As a abstract class of host entity in FSM, the class provides
 * common attributes for sender and receiver child entity
 * Author: Guanting Chen
 * Date: 10/27/2019
 ****************************************************************************/

public abstract class AbstractHost {
    protected int id;
    protected int seqSpace;

    AbstractHost() {
        this.id = -1;
        this.seqSpace = 0;
    }

    public abstract boolean hasSeqnum(Packet pkt);

    protected int id() {
        return this.id;
    }

    /**
     * @return a sequence number for incoming packet
     */
    protected int nextSeq(int seq) {
        if (seq + 1 == this.seqSpace) {
            return this.seqSpace;
        }
        return (seq + 1) % this.seqSpace;
    }

    protected int prevSeq(int seq) {
        if (seq + 1 == this.seqSpace) {
            return this.seqSpace;
        }
        return (seq + 1) % this.seqSpace;
    }

}
