import java.util.*;

/***************************************************************************
 * ClassName: Buffer
 * Date: 11/01/2019
 * Version: 1.0
 * Description: Resizeable buffer based on ArrayList and currently used by
 * sender. It stores packets sent and to be sent. Buffered packets are sorted
 * by the time they were buffered. When resizing needed, truncating sent packets
 * to allow more packets come in.
 * Author: Guanting Chen
 * Date: 11/01/2019
 ****************************************************************************/

public class Buffer<T> {
    private ArrayList<T> buff;
    private int capacity;
    private int size;
    public Buffer() {
        this.buff = new ArrayList<>(50);
        this.capacity = 50;
        this.size = 0;
    }

    public Buffer(int capacity) {
        this.capacity = capacity;
        this.buff = new ArrayList<>(this.capacity);
        this.size = 0;
    }

    public boolean add(T p) {
        if (isFull()) {
            return false;
        }
        this.buff.add(p);
        ++this.size;
        return true;
    }

    public T get(int index) {
        if (index < 0 || index >= this.size) {
            throw new IllegalArgumentException("Access illegal index of buff");
        }
        return this.buff.get(index);
    }

    public void set(int index, T element) {
        if (index < 0 || index >= this.size) {
            throw new IllegalArgumentException("Access illegal index of buff");
        }
        this.buff.set(index, element);
    }

//    // For send buffer only
//    public T[] getAll() {
//        return (T[]) this.buff.subList(0, size).toArray();
//    }

    public boolean isFull() {
        return this.size == this.capacity;
    }

    public void clear() {
        this.buff.clear();
        this.size = 0;
    }

    public int size() {
        return this.size;
    }

    public int getCapacity() {
        return this.capacity;
    }

    /**
     * When buffer is full, truncating buffer -> [base, buffCap)
     * trigger when size == capacity
     *
     * @param base window base index
     */
    public int resize(int base) {
        ArrayList<T> newBuff = new ArrayList<>();
        if (base < capacity) {
            // non empty window
            List<T> subList = buff.subList(base, capacity);
            newBuff.addAll(0, subList);
        }
        this.buff = newBuff;
        assert (capacity - base == newBuff.size());
        this.size = capacity - base;
        return size;
    }

    /**
     * Truncate window[base, next) from buffer as a new
     * buffer [0, next), used for timer buffer
     * @return the size of new buffer after truncating
     */
    public int truncate(int base, int end) {
        ArrayList<T> newBuff = new ArrayList<>();
        if (base < end) {
            // non empty window
            List<T> subList = buff.subList(base, end);
            newBuff.addAll(0, subList);
        }
        this.buff = newBuff;
        this.size = newBuff.size();
        return this.size;
    }

    public Iterator<T> iterator() {
        return new BufferIterator(this.size);
    }

    public Iterator<T> wholeIterator() {
        return new BufferIterator();
    }

    @Deprecated
    class BufferComparator implements Comparator<Packet> {
        @Override
        public int compare(Packet p1, Packet p2) {
            if (p1.getSeqnum() < p2.getSeqnum()) {
                return 1;
            } else {
                return -1;
            }
        }
    }

    private class BufferIterator implements Iterator<T> {
        int startIndex;
        int endIndex;

        public BufferIterator() {
            this.startIndex = 0;
            this.endIndex = Buffer.this.capacity;
        }

        public BufferIterator(int index) {
            this.startIndex = 0;
            this.endIndex = index;
        }

        // Checks if the next element exists
        @Override
        public boolean hasNext() {
            return this.startIndex < this.endIndex;
        }

        // moves the cursor/iterator to next element
        @Override
        public T next() {
            if (!hasNext()) throw new NoSuchElementException();
            int curIndex = startIndex;
            startIndex += 1;
            return Buffer.this.get(curIndex);
        }
    }

}
