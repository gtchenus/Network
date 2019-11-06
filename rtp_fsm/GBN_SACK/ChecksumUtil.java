/***************************************************************************
 * ClassName: ChecksumUtil
 * Date: 10/27/2019
 * Version: 1.0
 * Description: Simple checksum algorithm for GBN with sack, unable to detect
 * reordering bytes.
 *
 * Author: Guanting Chen
 * Date: 10/27/2019
 ****************************************************************************/

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ChecksumUtil {

    public static boolean isCorrupted(Packet pkt) {
        byte[] pktBtyeArr = getByteArray(pkt.getSeqnum(), pkt.getAcknum(), pkt.getPayload(), pkt.getSack());
        int sum = calculateSum(pktBtyeArr);
        sum += pkt.getChecksum();
        return sum != 0xFFFF;
    }

    public static int getCheckSum(int seq, int ack, Message msg, int[] sack) {
        byte[] buff = new byte[0];      // init an empty byte arr
        buff = getByteArray(seq, ack, msg.getData(), sack);
        // Final 1's complement value correction to 16-bits
        int checksum = ~calculateSum(buff);
        return checksum & 0xFFFF;
    }

    private static byte[] getByteArray(int seq, int ack, String str, int[] sack) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(intToByteArray(seq));
            out.write(intToByteArray(ack));
            out.write(str.getBytes("UTF-8"));
            for (int i = 0; i < sack.length; i++) {
                byte[] sackBytes = intToByteArray(sack[i]);
                out.write(sackBytes);
            }
        } catch (IOException ioe) {
            System.out.println("Failed to generate ByteArrayOutputStream of packet ");
        }
        return out.toByteArray();
    }

    private static int calculateSum(byte[] buf) {
        int length = buf.length;
        int i = 0;
        int sum = 0;
        int data;

        // Handle all pairs
        while (length > 1) {
            // Corrected to include @Andy's edits and various comments on Stack Overflow
            data = (((buf[i] << 8) & 0xFF00) | ((buf[i + 1]) & 0xFF));
            sum += data;
            // 1's complement carry bit correction in 16-bits (detecting sign extension)
            if ((sum & 0xFFFF0000) > 0) {
                sum = sum & 0xFFFF;
                sum += 1;
            }

            i += 2;
            length -= 2;
        }
        // Handle remaining byte in odd length buffers
        if (length > 0) {
            // Corrected to include @Andy's edits and various comments on Stack Overflow
            sum += (buf[i] << 8 & 0xFF00);
            // 1's complement carry bit correction in 16-bits (detecting sign extension)
            if ((sum & 0xFFFF0000) > 0) {
                sum = sum & 0xFFFF;
                sum += 1;
            }
        }
        return sum;     //  16 bit < sum < 32 bit
    }

    private static byte[] intToByteArray(int n) {
        byte[] result = new byte[4];
        result[0] = (byte) (n >> 24);
        result[1] = (byte) (n >> 16);
        result[2] = (byte) (n >> 8);
        result[3] = (byte) (n /*>> 0*/);
        return result;
    }

//    public static void main(String[] argv) {
//        int seq = 1;
//        int ack = 1;
//        int[] sack = new int[]{0, 1, 2, 3, 4};
//        Message msg = new Message("abc");
//        int checksum = getCheckSum(seq, ack, msg, sack);
//        System.out.println("Check sum: " + Integer.toBinaryString(checksum));
//
//        seq = 1;
//        ack = 1;
//        sack = new int[]{0, 1, 2, 3, 4};
//        int sum = calculateSum(getByteArray(seq, ack, "cba", sack));
//        System.out.println("Sum: " + Integer.toBinaryString(sum));
//        sum += checksum;
//        System.out.println("Validation sum: " + Integer.toBinaryString(sum & 0xFFFF));
//        boolean isCorrupted = (sum & 0xFFFF) != 0xFFFF;
//        System.out.println("IsCorrupted: " + isCorrupted);
//    }
}
