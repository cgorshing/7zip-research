package research;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SevenZipDecoder {
  public static final int kEnd = 0x00;
  public static final int kHeader = 0x01;
  public static final int kAdditionalStreamsInfo = 0x03;
  public static final int kMainStreamsInfo = 0x04;
  public static final int kPackInfo = 0x06;
  public static final int kSize = 0x09;
  public static final int kCrc = 0x0A;

  public static void main(String[] args) throws IOException {
    FileInputStream inputStream = new FileInputStream("C:\\tmp\\chad.7z");

    byte[] header = new byte[6];

    int read = inputStream.read(header);

    assert read == 6;
    assert header[0] == '7';
    assert header[1] == 'z';
    assert header[2] == (byte) 0xBC;
    assert header[3] == (byte) 0xAF;
    assert header[4] == (byte) 0x27;
    assert header[5] == (byte) 0x1C;

    byte[] version = new byte[2];

    read = inputStream.read(version);
    assert read == 2;
    assert version[0] == (byte) 0;
    assert version[1] == (byte) 3;


    byte[] startHeaderCRC = new byte[4];

    read = inputStream.read(startHeaderCRC);
    assert read == 4;

    byte a = 15;
    int crc = makeByteBuffer(startHeaderCRC).getInt();
    assert crc == -2078405524;

    byte[] startHeader = new byte[20];
    read = inputStream.read(startHeader);
    assert read == 20;
    ByteBuffer byteBuffer = makeByteBuffer(startHeader);
    long nextHeaderOffset = byteBuffer.getLong();
    assert nextHeaderOffset == 3307;
    long nextHeaderSize = byteBuffer.getLong(8);
    assert nextHeaderSize == 82;
    int nextHeaderCrc = byteBuffer.getInt(16);
    assert nextHeaderCrc == -266949232;

    long skip = inputStream.skip(nextHeaderOffset);

    int possibleHeader = inputStream.read();

    assert possibleHeader == kHeader;

    int next = inputStream.read();
    assert next == kMainStreamsInfo;

    //byte [] mainStreamsInfo = new byte[]
    next = inputStream.read();
    assert next == kPackInfo;

    long foo = read7ZipUInt64(inputStream);

    inputStream.close();
  }

  private static long read7ZipUInt64(FileInputStream inputStream) throws IOException {
    byte firstByte = (byte)inputStream.read();
    byte mask = (byte)0x80;
    long value = 0;
    for (int i = 0; i < 8; ++i) {
      if ((firstByte & mask) == 0) {
        long highPart = firstByte & (mask - 1);
        value += (highPart << (i * 8));
        return value;
      }

      //ThrowEndOfData();
      long nextByte = (long)inputStream.read();
      value |= (nextByte << (8 * i));
      mask >>= 1;
    }
    return value;
  }

  private static ByteBuffer makeByteBuffer(byte[] startHeaderCRC) {
    return ByteBuffer.wrap(startHeaderCRC).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
  }
}
