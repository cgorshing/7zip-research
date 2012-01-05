package research;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SevenZipDecoder {
  public static final int kEnd0x00 = 0x00;
  public static final int kHeader0x01 = 0x01;
  public static final int kAdditionalStreamsInfo0x03 = 0x03;
  public static final int kMainStreamsInfo0x04 = 0x04;
  public static final int kPackInfo0x06 = 0x06;
  public static final int kUnPackINfo0x07 = 0x07;
  public static final int kSubStreamsInfo0x08 = 0x08;
  public static final int kSize0x09 = 0x09;
  public static final int kCrc0x0A = 0x0A;
  public static final int kFolder0x0B = 0x0B;

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

    assert kHeader0x01 == inputStream.read();
    assert kMainStreamsInfo0x04 == inputStream.read();
    assert kPackInfo0x06 == inputStream.read();

    long packOffset = read7ZipUInt64(inputStream);
    long numberOfStreams = read7ZipUInt64(inputStream);

    assert numberOfStreams == 1;

    assert kSize0x09 == inputStream.read();

    for (int i = 0; i < numberOfStreams; ++i) {
      long packsize = read7ZipUInt64(inputStream);

      assert packsize == 3307 : packsize;
    }

    assert kEnd0x00 == inputStream.read(); // This ends the PackInfo section

    //figure out what optional section is next
    assert kUnPackINfo0x07 == inputStream.read();
    assert kFolder0x0B == inputStream.read();
    long numOfFolders = read7ZipUInt64(inputStream);
    assert 1 == numOfFolders;
    int external = inputStream.read();
    if (external == 0) {
      //Folders[NumFolders]
      assert 1 == inputStream.read(); // Num of Coders;
      int val = inputStream.read();
      int codecIdSize = 0x0F & val;
      boolean isComplexCoder = (0x10 & val) != 0;
      boolean areAttributes = (0x20 & val) != 0;

      byte [] codecId = new byte[codecIdSize];
      assert codecIdSize == inputStream.read(codecId);

      if (isComplexCoder) {
        throw new RuntimeException("Not sure what to do with this.");
      }

      if (areAttributes) {
        int propertiesSize = (int)read7ZipUInt64(inputStream);
        byte [] properties = new byte[propertiesSize];
        assert propertiesSize == inputStream.read(properties);
      }
    }
    else if (external == 1) {
      //UINT64 DataStreamIndex
    }

    assert 0x0C == inputStream.read();

    for (int i = 0; i < numOfFolders; ++i) {
      for (int j = 0; j < numberOfStreams; ++j) {
        long l = read7ZipUInt64(inputStream);
        assert 4242 == l : l;
      }
    }

    assert kEnd0x00 == inputStream.read();
    assert kSubStreamsInfo0x08 == inputStream.read();

    int nextSection = inputStream.read();
    if (nextSection == kCrc0x0A) {

    }
    else {
      throw new RuntimeException("Unimplemented");
    }

    inputStream.close();
  }

  private static long read7ZipUInt64(FileInputStream inputStream) throws IOException {
    //ThrowEndOfData();
    int firstByte = inputStream.read();
    int mask = 0x80;
    long value = 0;
    for (int i = 0; i < 8; ++i) {
      if ((firstByte & mask) == 0) {
        int highPart = ((firstByte & 0xFF) & (mask - 1));
        value += (highPart << (i * 8));
        return value;
      }

      //ThrowEndOfData();
      int nextByte = inputStream.read();
      value |= (nextByte & 0xFF) << (8 * i);

      mask >>>= 1;
    }
    return value;
  }

  private static ByteBuffer makeByteBuffer(byte[] startHeaderCRC) {
    return ByteBuffer.wrap(startHeaderCRC).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
  }
}
