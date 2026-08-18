import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Streams 300 MiB through the same 64 KiB independently authenticated AES-GCM frame layout used
 * by NovaAc v7. It intentionally retains no archive-sized byte array.
 */
public final class FramedNovaAcStressTest {
  private static final int FRAME = 64 * 1024;
  private static final long TOTAL = 300L * 1024L * 1024L;
  private static final byte[] HEADER_AAD = "formatVersion=8\nprotocol=NovaBytecode-v8-FramedGcm\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

  public static void main(String[] args) throws Exception {
    File output = new File(args.length > 0 ? args[0] : "framed_novaac_stress.bin");
    KeyGenerator generator = KeyGenerator.getInstance("AES");
    generator.init(256);
    SecretKey key = generator.generateKey();
    byte[] baseNonce = new byte[12];
    new SecureRandom().nextBytes(baseNonce);
    byte[] buffer = new byte[FRAME];
    MessageDigest writeDigest = MessageDigest.getInstance("SHA-256");
    long writtenPlain = 0L;
    long frameIndex = 0L;
    try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(output), FRAME))) {
      while (writtenPlain < TOTAL) {
        int length = (int) Math.min(buffer.length, TOTAL - writtenPlain);
        fill(buffer, length, writtenPlain);
        writeDigest.update(buffer, 0, length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce(baseNonce, frameIndex)));
        cipher.updateAAD(HEADER_AAD);
        cipher.updateAAD(longBytes(frameIndex));
        byte[] encrypted = cipher.doFinal(buffer, 0, length);
        out.writeLong(frameIndex);
        out.writeInt(length);
        out.writeInt(encrypted.length);
        out.write(encrypted);
        writtenPlain += length;
        frameIndex++;
      }
      out.writeLong(-1L);
      out.writeInt(0);
      out.writeInt(0);
    }
    MessageDigest readDigest = MessageDigest.getInstance("SHA-256");
    long readPlain = 0L;
    long expectedFrame = 0L;
    try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(output), FRAME))) {
      while (true) {
        long index = in.readLong();
        int plainLength = in.readInt();
        int cipherLength = in.readInt();
        if (index == -1L) break;
        if (index != expectedFrame || plainLength < 1 || plainLength > FRAME || cipherLength < plainLength + 16 || cipherLength > plainLength + 32) throw new IllegalStateException("Invalid frame");
        byte[] encrypted = new byte[cipherLength];
        in.readFully(encrypted);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce(baseNonce, index)));
        cipher.updateAAD(HEADER_AAD);
        cipher.updateAAD(longBytes(index));
        byte[] plain = cipher.doFinal(encrypted);
        if (plain.length != plainLength) throw new IllegalStateException("Length mismatch");
        readDigest.update(plain);
        readPlain += plain.length;
        expectedFrame++;
      }
    }
    if (readPlain != TOTAL || !MessageDigest.isEqual(writeDigest.digest(), readDigest.digest())) throw new IllegalStateException("Digest mismatch");
    System.out.println("PASS totalPlainBytes=" + readPlain + " frames=" + expectedFrame + " archiveBytes=" + output.length());
  }

  private static void fill(byte[] buffer, int length, long offset) {
    for (int i = 0; i < length; i++) buffer[i] = (byte) ((offset + i) * 31L + 17L);
  }

  private static byte[] nonce(byte[] base, long index) {
    byte[] nonce = base.clone();
    for (int offset = 0; offset < 8; offset++) nonce[nonce.length - 1 - offset] ^= (byte) (index >>> (offset * 8));
    return nonce;
  }

  private static byte[] longBytes(long value) {
    byte[] out = new byte[8];
    for (int i = 0; i < 8; i++) out[i] = (byte) (value >>> ((7 - i) * 8));
    return out;
  }
}
