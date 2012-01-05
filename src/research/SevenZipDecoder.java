package research;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

public class SevenZipDecoder {
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
      int allAreDefined = inputStream.read();
      if (allAreDefined == 0) {

      }

      byte [] crcData = new byte[4];
      assert 4 == inputStream.read(crcData);
      assert kEnd0x00 == inputStream.read();
    }
    else {
      throw new RuntimeException("Unimplemented");
    }

    assert kEnd0x00 == inputStream.read();

    assert kFilesInfo0x05 == inputStream.read();
    long numOfFiles = read7ZipUInt64(inputStream);
    assert 1 == numOfFiles;

    int propertyType = inputStream.read();

    while (propertyType != kEnd0x00) {
      long propertySize = read7ZipUInt64(inputStream);

      if (kNames0x11 == propertyType) {
        int external2 = inputStream.read();
        long dataIndex = 0;
        if (external2 != 0)
          dataIndex = read7ZipUInt64(inputStream);

        for (int i = 0; i < numOfFiles; ++i) {
          byte [] ch = new byte[4096];
          int index = 0;
          assert 2 == inputStream.read(ch, 0, 2);
          while (!(ch[index] == 0 && ch[index+1] == 0)) {
            ++index;
            assert 2 == inputStream.read(ch, index, 2);
          }

          assert "chad.gpg".equals(new String(ch).trim()) : new String(ch);
        }
      }
      else if (kMTime0x14 == propertyType) {
        int allDefined = inputStream.read();

        if (allDefined == 0) {
          throw new RuntimeException("Unimplemented");
        }

        int external2 = inputStream.read();
        long dataIndex = 0;
        if (external2 != 0)
          dataIndex = read7ZipUInt64(inputStream);

        for (int i = 0; i < numOfFiles; ++i) {
          long modifiedAt = read7ZipUInt64(inputStream);
          System.out.println(modifiedAt);
          //assert 1 != modifiedAt;
        }
      }
      else if (kAttributes0x15 == propertyType) {
        int allDefined = inputStream.read();

        if (allDefined == 0) throw new RuntimeException("Unimplemented");

        int external2 = inputStream.read();

        long dataIndex = 0;
        if (external2 != 0)
          dataIndex = read7ZipUInt64(inputStream);

        byte [] attribute = new byte[4];
        inputStream.read(attribute);
        System.out.println(attribute);
      }

      propertyType = inputStream.read();
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
