package research;

import net.contrapunctus.lzma.LzmaInputStream;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

public class SevenZipDecoder {
  public static final int HeaderSize = 32;
  public static final int kEnd0x00 = 0x00;
  public static final int kHeader0x01 = 0x01;
  public static final int kAdditionalStreamsInfo0x03 = 0x03;
  public static final int kMainStreamsInfo0x04 = 0x04;
  public static final int kFilesInfo0x05 = 0x05;
  public static final int kPackInfo0x06 = 0x06;
  public static final int kUnPackINfo0x07 = 0x07;
  public static final int kSubStreamsInfo0x08 = 0x08;
  public static final int kSize0x09 = 0x09;
  public static final int kCrc0x0A = 0x0A;
  public static final int kFolder0x0B = 0x0B;
  public static final int kNames0x11 = 0x11;
  public static final int kMTime0x14 = 0x14;
  public static final int kAttributes0x15 = 0x15;

  public static void foo(byte [] fileDataByteArray) throws IOException {
    //System.getProperty("DEBUG_LzmaStreams"); }
    //System.setProperty("DEBUG_LzmaStreams", "1");
    System.setProperty("DEBUG_ConcurrentBuffer", "1");

    Checksum ck = new Adler32();
    int BUFSIZE = 8192;

    int byteCount = 0;

    ByteArrayInputStream bais = new ByteArrayInputStream(fileDataByteArray);
    LzmaInputStream li = new LzmaInputStream(bais);
    byte[] buf = new byte[BUFSIZE];
    ck.reset();
    int k = li.read(buf);
    byteCount = 0;
    while (k > 0) {
      byteCount += k;
      ck.update(buf, 0, k);
      k = li.read(buf);
    }
    System.out.printf("%d bytes decompressed, checksum %X\n", byteCount, ck.getValue());
    System.out.printf("ck.getValue() -> %d\n", ck.getValue());
  }

  public static void main(String[] args) throws IOException {
    //FileInputStream input = new FileInputStream("C:\\tmp\\chad.7z");
    RandomAccessFile input = new RandomAccessFile("C:\\tmp\\chad.7z", "r");

    byte[] header = new byte[6];

    int read = input.read(header);

    assert read == 6;
    assert header[0] == '7';
    assert header[1] == 'z';
    assert header[2] == (byte) 0xBC;
    assert header[3] == (byte) 0xAF;
    assert header[4] == (byte) 0x27;
    assert header[5] == (byte) 0x1C;

    byte[] version = new byte[2];

    read = input.read(version);
    assert read == 2;
    assert version[0] == (byte) 0;
    assert version[1] == (byte) 3;


    byte[] startHeaderCRC = new byte[4];

    read = input.read(startHeaderCRC);
    assert read == 4;

    byte a = 15;
    int crc = makeByteBuffer(startHeaderCRC).getInt();
    assert crc == -2078405524;

    byte[] startHeader = new byte[20];
    read = input.read(startHeader);
    assert read == 20;
    ByteBuffer byteBuffer = makeByteBuffer(startHeader);
    long nextHeaderOffset = byteBuffer.getLong();
    assert nextHeaderOffset == 3307;
    long nextHeaderSize = byteBuffer.getLong(8);
    assert nextHeaderSize == 82;
    int nextHeaderCrc = byteBuffer.getInt(16);
    assert nextHeaderCrc == -266949232;

    input.seek(HeaderSize + nextHeaderOffset);
    //long skip = input.skip(nextHeaderOffset);

    assert kHeader0x01 == input.read();
    assert kMainStreamsInfo0x04 == input.read();
    assert kPackInfo0x06 == input.read();

    long packOffset = read7ZipUInt64(input);
    long numberOfStreams = read7ZipUInt64(input);

    assert numberOfStreams == 1;

    assert kSize0x09 == input.read();

    for (int i = 0; i < numberOfStreams; ++i) {
      int packsize = (int)read7ZipUInt64(input);

      assert packsize == 3307 : packsize;

      long pointer = input.getFilePointer();
      byte [] data = new byte[packsize];
      input.seek(HeaderSize + packOffset);
      input.read(data);
      foo(data);
      input.seek(pointer);
    }

    assert kEnd0x00 == input.read(); // This ends the PackInfo section

    //figure out what optional section is next
    assert kUnPackINfo0x07 == input.read();
    assert kFolder0x0B == input.read();
    long numOfFolders = read7ZipUInt64(input);
    assert 1 == numOfFolders;
    int external = input.read();
    if (external == 0) {
      //Folders[NumFolders]
      assert 1 == input.read(); // Num of Coders;
      int val = input.read();
      int codecIdSize = 0x0F & val;
      boolean isComplexCoder = (0x10 & val) != 0;
      boolean areAttributes = (0x20 & val) != 0;

      byte[] codecId = new byte[codecIdSize];
      assert codecIdSize == input.read(codecId);

      if (isComplexCoder) {
        throw new RuntimeException("Not sure what to do with this.");
      }

      if (areAttributes) {
        int propertiesSize = (int) read7ZipUInt64(input);
        byte[] properties = new byte[propertiesSize];
        assert propertiesSize == input.read(properties);
      }
    } else if (external == 1) {
      //UINT64 DataStreamIndex
    }

    assert 0x0C == input.read();

    for (int i = 0; i < numOfFolders; ++i) {
      for (int j = 0; j < numberOfStreams; ++j) {
        long l = read7ZipUInt64(input);
        assert 4242 == l : l;
      }
    }

    assert kEnd0x00 == input.read();
    assert kSubStreamsInfo0x08 == input.read();

    int nextSection = input.read();
    if (nextSection == kCrc0x0A) {
      int allAreDefined = input.read();
      if (allAreDefined == 0) {

      }

      byte[] crcData = new byte[4];
      assert 4 == input.read(crcData);
      assert kEnd0x00 == input.read();
    } else {
      throw new RuntimeException("Unimplemented");
    }

    assert kEnd0x00 == input.read();

    assert kFilesInfo0x05 == input.read();
    long numOfFiles = read7ZipUInt64(input);
    assert 1 == numOfFiles;

    int propertyType = input.read();

    while (propertyType != kEnd0x00) {
      long propertySize = read7ZipUInt64(input);

      if (kNames0x11 == propertyType) {
        int external2 = input.read();
        long dataIndex = 0;
        if (external2 != 0)
          dataIndex = read7ZipUInt64(input);

        for (int i = 0; i < numOfFiles; ++i) {
          byte[] ch = new byte[4096];
          int index = 0;
          assert 2 == input.read(ch, 0, 2);
          while (!(ch[index] == 0 && ch[index + 1] == 0)) {
            ++index;
            assert 2 == input.read(ch, index, 2);
          }

          assert "chad.gpg".equals(new String(ch).trim()) : new String(ch);
        }
      } else if (kMTime0x14 == propertyType) {
        int allDefined = input.read();

        if (allDefined == 0) {
          throw new RuntimeException("Unimplemented");
        }

        int external2 = input.read();
        long dataIndex = 0;
        if (external2 != 0)
          dataIndex = read7ZipUInt64(input);

        for (int i = 0; i < numOfFiles; ++i) {
          long modifiedAt = read7ZipUInt64(input);
        }
      } else if (kAttributes0x15 == propertyType) {
        int allDefined = input.read();

        if (allDefined == 0) throw new RuntimeException("Unimplemented");

        int external2 = input.read();

        long dataIndex = 0;
        if (external2 != 0)
          dataIndex = read7ZipUInt64(input);

        byte[] attribute = new byte[4];
        input.read(attribute);
      }

      propertyType = input.read();
    }

    input.close();
  }

  private static long read7ZipUInt64(RandomAccessFile input) throws IOException {
    //ThrowEndOfData();
    int firstByte = input.read();
    int mask = 0x80;
    long value = 0;
    for (int i = 0; i < 8; ++i) {
      if ((firstByte & mask) == 0) {
        int highPart = ((firstByte & 0xFF) & (mask - 1));
        value += (highPart << (i * 8));
        return value;
      }

      //ThrowEndOfData();
      int nextByte = input.read();
      value |= (nextByte & 0xFF) << (8 * i);

      mask >>>= 1;
    }
    return value;
  }

  private static ByteBuffer makeByteBuffer(byte[] startHeaderCRC) {
    return ByteBuffer.wrap(startHeaderCRC).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
  }
}
