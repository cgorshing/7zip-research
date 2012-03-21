package research;

import lzma.sdk.lzma.Decoder;
import lzma.streams.LzmaInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

public class SevenZipDecoder {
    public static final int BUFSIZE = 8192;

    public StartHeader StartHeader;
    public List<Header> Headers = new ArrayList<Header>();

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
    public static final int kAnti0x10 = 0x10;
    public static final int kCrc0x0A = 0x0A;
    public static final int kFolder0x0B = 0x0B;
    public static final int kEmptyStream0x0E = 0x0E;
    public static final int kEmptyFile0x0F = 0x0F;
    public static final int kNames0x11 = 0x11;
    public static final int kCreationTime0x12 = 0x12;
    public static final int kLastAccessTime0x13 = 0x13;
    public static final int kLastWriteTime0x14 = 0x14;
    public static final int kAttributes0x15 = 0x15;

    public static void inflate(byte[] fileDataByteArray) throws IOException {
        //http://docs.bugaco.com/7zip/7zFormat.txt
        //I can't find anything (yet) to denote what kind of CRC 7zip uses
        //I don't think it is Adler32 though
        //Checksum ck = new Adler32();
        CRC32 ck = new CRC32();

        int byteCount = 0;

        ByteArrayInputStream bais = new ByteArrayInputStream(fileDataByteArray);
        LzmaInputStream li = new LzmaInputStream(bais, new Decoder());

        byte[] buf = new byte[BUFSIZE];
        ck.reset();
        int k = li.read(buf);

        while (k > 0) {
            byteCount += k;
            ck.update(buf, 0, k);
            k = li.read(buf);
        }

        System.out.printf("%d bytes decompressed, checksum %X\n", byteCount, ck.getValue());
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

        int crc = makeByteBuffer(startHeaderCRC).getInt();
        System.out.println("CRC: " + crc);

        CRC32 blah = new CRC32();

        byte[] startHeader = new byte[20];
        read = input.read(startHeader);
        assert read == 20;
        blah.update(startHeader);
        System.out.println("CRC32: " + blah.getValue());
        ByteBuffer byteBuffer = makeByteBuffer(startHeader);
        long nextHeaderOffset = byteBuffer.getLong();
        System.out.println("nextHeaderOffset: " + nextHeaderOffset);
        long nextHeaderSize = byteBuffer.getLong(8);
        System.out.println("nextHeaderSize: " + nextHeaderSize);
        int nextHeaderCrc = byteBuffer.getInt(16);
        System.out.println("nextHeaderCrc: " + nextHeaderCrc);

        input.seek(HeaderSize + nextHeaderOffset);

        bombOut(kHeader0x01 == input.read());
        bombOut(kMainStreamsInfo0x04 == input.read());
        bombOut(kPackInfo0x06 == input.read());

        long packOffset = read7ZipUInt64(input);
        long numberOfStreams = read7ZipUInt64(input);

        System.out.println("numberOfStreams: " + numberOfStreams);

        bombOut(kSize0x09 == input.read());

        for (int i = 0; i < numberOfStreams; ++i) {
            int packSize = (int) read7ZipUInt64(input);

            System.out.println("packSize: " + packSize);

            long pointer = input.getFilePointer();
            byte[] data = new byte[packSize];
            input.seek(HeaderSize + packOffset);
            input.read(data);
            inflate(data);

            input.seek(pointer);
        }

        bombOut(kEnd0x00 == input.read()); // This ends the PackInfo section

        //figure out what optional section is next
        bombOut(kUnPackINfo0x07 == input.read());
        bombOut(kFolder0x0B == input.read());
        long numOfFolders = read7ZipUInt64(input);

        System.out.println("numOfFolders: " + numOfFolders);

        int external = input.read();
        if (external == 0) {
            //Folders[NumFolders]
            int numOfCoders = input.read();
            System.out.println("numOfCoders: " + numOfCoders); // Num of Coders;
            int val = input.read();
            int codecIdSize = 0x0F & val;
            boolean isComplexCoder = (0x10 & val) != 0;
            boolean areAttributes = (0x20 & val) != 0;

            byte[] codecId = new byte[codecIdSize];
            bombOut(codecIdSize == input.read(codecId));

            if (isComplexCoder) {
                throw new RuntimeException("Not sure what to do with this.");
            }

            if (areAttributes) {
                int propertiesSize = (int) read7ZipUInt64(input);
                byte[] properties = new byte[propertiesSize];
                bombOut(propertiesSize == input.read(properties));
            }
        }
        else if (external == 1) {
            //UINT64 DataStreamIndex
            throw new RuntimeException("Unimplemented");
        }

        if (0x0C != input.read()) throw new RuntimeException("Fail");

        for (int i = 0; i < numOfFolders; ++i) {
            for (int j = 0; j < numberOfStreams; ++j) {
                long l = read7ZipUInt64(input);
                System.out.println("file size: " + l);
            }
        }

        bombOut(kEnd0x00 == input.read());
        bombOut(kSubStreamsInfo0x08 == input.read());

        int nextSection = input.read();
        if (nextSection == kCrc0x0A) {
            int allAreDefined = input.read();
            if (allAreDefined == 0) {
                throw new RuntimeException("Unimplemented");
            }

            byte[] crcData = new byte[4];
            input.read(crcData);
            bombOut(kEnd0x00 == input.read());
        }
        else {
            throw new RuntimeException("Unimplemented");
        }

        bombOut(kEnd0x00 == input.read());

        bombOut(kFilesInfo0x05 == input.read());
        long numOfFiles = read7ZipUInt64(input);
        System.out.println("numOfFiles: " + numOfFiles);

        int propertyType = input.read();

        while (propertyType != kEnd0x00) {
            long propertySize = read7ZipUInt64(input);

            if (kEmptyStream0x0E == propertyType) {
                System.out.println("Found kEmptyStream0x0E");
                for (int i = 0; i < numOfFiles; ++i) {
                    int isEmptyStream = input.read(new byte[1]);
                }
            }
            else if (kEmptyFile0x0F == propertyType) {
                System.out.println("Found kEmptyFile0x0F");
            }
            else if (kAnti0x10 == propertyType) {
                System.out.println("Found kAnti0x10");
            }
            else if (kNames0x11 == propertyType) {
                int external2 = input.read();
                long dataIndex = 0;
                if (external2 != 0)
                    dataIndex = read7ZipUInt64(input);

                for (int i = 0; i < numOfFiles; ++i) {
                    byte[] ch = new byte[4096];
                    int index = 0;
                    input.read(ch, 0, 2);
                    while (!(ch[index] == 0 && ch[index + 1] == 0)) {
                        ++index;
                        input.read(ch, index, 2);
                    }

                    System.out.println("file name: " + new String(ch).trim());
                }
            }
            else if (kCreationTime0x12 == propertyType) {
                System.out.println("Found kCreationTime");
            }
            else if (kLastAccessTime0x13 == propertyType) {
                System.out.println("Found kLastAccessTime0x13");
            }
            else if (kLastWriteTime0x14 == propertyType) {
                System.out.println("Found kLastWriteTime");
                int allDefined = input.read(new byte[1]);

                if (allDefined == 0) {
                    throw new RuntimeException("Unimplemented");
                }

                int external2 = input.read(new byte[1]);
                long dataIndex = 0;
                if (external2 != 0)
                    dataIndex = read7ZipUInt64(input);

                for (int i = 0; i < numOfFiles; ++i) {
                    byte[] timeBuffer = new byte[4];
                    input.read(timeBuffer);
                    int modifiedAt = makeByteBuffer(timeBuffer).getInt();
                    Date modifiedDate = new Date(modifiedAt * 1000);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
                    System.out.println("modified: " + sdf.format(modifiedDate));
                }
            }
            else if (kAttributes0x15 == propertyType) {
                System.out.println("Found kAttributes0x15");
                int allDefined = input.read(new byte[1]);

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

    private static void bombOut(boolean expression) {
        if (!expression) throw new RuntimeException("Fail");
    }
}
