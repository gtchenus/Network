import java.util.Iterator;
import java.util.NoSuchElementException;

/***************************************************************************
 * ClassName: SendWindow
 * Date: 10/27/2019
 * Version: 1.0
 * Description: SendWindow is send window object of variable size, and is
 * attached with a array based buffer. The class provides API to manipulate
 * send window to get/update its state when the window is moving on buffer.
 * Sendwindow is iterable in terms of elements inside the window.
 * Author: Guanting Chen
 * Date: 10/27/2019
 ****************************************************************************/

public class SendWindow implements Iterable<Integer> {
    private int base;       // Index of window base(current received cumulative ack)
                            // base will not move if no newly buffered packets
    private int next;       // index of next packet to send, normally at the end of window
                            // base = next happens when send window is empty
    private int size;       // Invariant, default: 10
    private int buffCap;    // capacity of send buffer which sndWindow is sliding on
    private int buffSize;   // synchronous size of current buffer;

    public SendWindow(int size, int capacity) {
        this.base = 0;
        this.next = 0;
        this.size = size;
        this.buffCap = capacity;

    }

    public int getBase() {
        return this.base;
    }


    /**
     * if reach the end of buffer,
     * next == buffSize,  stop moving until having a newly buffered entry
     */
    private boolean reachEndOfBuffer() {
        return this.next == this.buffSize;
    }

    /**
     * No more entries to send in both send window and buffer
     */
    public boolean isEmpty() {
        return this.base == this.next && reachEndOfBuffer();
    }

    /**
     * Get the next adjacent index outside the window
     * if sndWindow reaches the end of existing buffered entries,
     * returned index is out of buffer if reachEndOfBuffer()
     */
    public int getEnd() {
        if (reachEndOfBuffer() || this.buffSize <= this.base + this.size) return this.buffSize;
        else return this.base + this.size;
    }

    public int getNext() {
        return this.next;
    }

    public int size() {
        return this.size;
    }

    /**
     * Whether send window contains an index of sent entries
     */
    public boolean contains(int index) {
        return index >= this.base && index < this.next;
    }

    /**
     * Update when buffer resizing and window sliding
     * Base can catch up with next (=next) if window is empty
     * @param baseIndex range: [0, bufferSize]
     */
    public void setBase(int baseIndex) {
        if (baseIndex < 0 || baseIndex > this.next
                || baseIndex > buffSize) {
            throw new IllegalArgumentException("Illegal send window base, out of bound");
        }
        this.base = baseIndex;
    }

    /**
     * Update when buffer resizing and a sent packet
     * Next should be always set to a index > base except window is empty
     * @param nextIndex range: [base, getEnd()]
     */
    public void setNext(int nextIndex) {
        if (nextIndex < 0 || nextIndex > getEnd() ||
                (!isEmpty() && nextIndex <= this.base))  {
            throw new IllegalArgumentException("Illegal send window index of next, out of bound");
        }
        this.next = nextIndex;
    }

    /**
     * Update when buffer resizing and a new buffered packet
     * @param buffSize that should be synchronized immediately
     */
    public void setBuffSize(int buffSize) {
        if (buffSize < 0 || buffSize > this.buffCap) {
            throw new IllegalArgumentException("Illegal buffer size, out of boud");
        }
        this.buffSize = buffSize;
    }

    /**
     * Identify whether sndWindow is full
     * @return true and refuse to send more packets
     */
    public boolean isFull() {
        return (this.getEnd() - this.base) == this.size;
    }

    public void reset(int buffSize, int base, int next) {
        this.buffSize = buffSize;
        this.base = base;
        this.next = next;
    }

    /**
     * Used by iterator only, index in range [base, end] inclusive
     */
    private boolean indexOutOfRange(int index) {
        if (index < 0 || index < this.base || index > getEnd()) {
            return true;
        }
        return false;
    }

    /**
     * Window index iterator
     * @return a iterator iterating through entire window
     */
    @Override
    public Iterator<Integer> iterator() {
        return new WindowIterator();
    }

    /**
     * Window index iterator
     * @return a iterator iterating through [index, ) of window
     */
    public Iterator<Integer> iterator(int index) {

        if (indexOutOfRange(index)) {
            throw new IllegalArgumentException("Iterator's start index is out of range of sendwindow");
        }
        return new WindowIterator(index);
    }

    /**
     * Window index iterator
     * @return a iterator iterating through [startIndex, endIndex) of window
     */
    public Iterator<Integer> iterator(int startIndex, int endIndex) {

        if (indexOutOfRange(startIndex) || indexOutOfRange(endIndex)) {
            throw new IllegalArgumentException("Iterator's start index is out of range of sendwindow");
        }
        return new WindowIterator(startIndex, endIndex);
    }

    public class WindowIterator implements Iterator<Integer> {
        private int startIndex;
        private int endIndex;
        // constructor
        public WindowIterator() {
            startIndex = SendWindow.this.getBase();
            endIndex = SendWindow.this.getEnd();
        }

        public WindowIterator(int startIndex) {
            this.startIndex = startIndex;
            this.endIndex = SendWindow.this.getEnd();
        }

        public WindowIterator(int startIndex, int endIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        // Checks if the next element exists
        @Override
        public boolean hasNext() {
            return this.startIndex < this.endIndex;
        }

        // moves the cursor/iterator to next element
        @Override
        public Integer next() {
            if (!hasNext())   throw new NoSuchElementException();
            int curIndex = startIndex;
            startIndex += 1;
            return curIndex;
        }
    }

}
