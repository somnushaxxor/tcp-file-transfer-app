package ru.nsu.fit.kolesnik.tcpfiletransferapp.protocol;

import lombok.Getter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Getter
public class FileTransferMessage {

    public static final int MAX_DATA_SIZE = 32768;

    private final FileTransferMessageType type;
    private int fileNameUtf8Size;
    private String fileName;
    private long fileSize;
    private int dataSize;
    private byte[] data;

    public FileTransferMessage(FileTransferMessageType type) {
        this.type = type;
    }

    public FileTransferMessage(FileTransferMessageType type, int fileNameUtf8Size, String fileName, long fileSize) {
        this.type = type;
        this.fileNameUtf8Size = fileNameUtf8Size;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }

    public FileTransferMessage(FileTransferMessageType type, int dataSize, byte[] data) {
        this.type = type;
        this.dataSize = dataSize;
        this.data = data;
    }

    public byte[] getBytes() {
        switch (type) {
            case INIT -> {
                return ByteBuffer.allocate(Integer.BYTES + Integer.BYTES + fileNameUtf8Size + Long.BYTES)
                        .putInt(type.ordinal())
                        .putInt(fileNameUtf8Size)
                        .put(fileName.getBytes(StandardCharsets.UTF_8))
                        .putLong(fileSize)
                        .array();
            }
            case DATA -> {
                return ByteBuffer.allocate(Integer.BYTES + Integer.BYTES + dataSize)
                        .putInt(type.ordinal())
                        .putInt(dataSize)
                        .put(data)
                        .array();
            }
            default -> {
                return ByteBuffer.allocate(Integer.BYTES).putInt(type.ordinal()).array();
            }
        }
    }

    public static void sendFileTransferMessage(FileTransferMessage message, DataOutputStream outputStream) throws IOException {
        byte[] messageBytes = message.getBytes();
        outputStream.write(messageBytes);
        outputStream.flush();
    }

    public static FileTransferMessage receiveFileTransferMessage(DataInputStream inputStream) throws IOException {
        FileTransferMessageType type = FileTransferMessageType.values()[inputStream.readInt()];
        switch (type) {
            case INIT -> {
                int fileNameUtf8Size = inputStream.readInt();
                byte[] fileNameUtf8Bytes = new byte[fileNameUtf8Size];
                inputStream.readFully(fileNameUtf8Bytes);
                String fileName = new String(fileNameUtf8Bytes, StandardCharsets.UTF_8);
                long fileSize = inputStream.readLong();
                return new FileTransferMessage(type, fileNameUtf8Size, fileName, fileSize);
            }
            case DATA -> {
                int dataSize = inputStream.readInt();
                byte[] data = new byte[dataSize];
                inputStream.readFully(data);
                return new FileTransferMessage(type, dataSize, data);
            }
            default -> {
                return new FileTransferMessage(type);
            }
        }
    }

}
