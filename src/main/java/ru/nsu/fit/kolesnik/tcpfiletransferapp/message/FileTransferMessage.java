package ru.nsu.fit.kolesnik.tcpfiletransferapp.message;

import lombok.Getter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Getter
public class FileTransferMessage {

    public final static int MAX_DATA_SIZE = 32768;

    private final FileTransferMessageType type;
    private Integer fileNameUtf8Size;
    private String fileName;
    private Long fileSize;
    private Integer dataSize;
    private byte[] data;

    public FileTransferMessage(FileTransferMessageType type) {
        this.type = type;
    }

    public FileTransferMessage(FileTransferMessageType type, String fileName, long fileSize) {
        this.type = type;
        fileNameUtf8Size = fileName.getBytes(StandardCharsets.UTF_8).length;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }

    public FileTransferMessage(FileTransferMessageType type, byte[] data) {
        this.type = type;
        dataSize = data.length;
        this.data = data;
    }

    public FileTransferMessage(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        type = FileTransferMessageType.getByCode(byteBuffer.get());
        switch (type) {
            case INIT -> {
                fileNameUtf8Size = byteBuffer.getInt();
                byte[] fileNameUtf8Bytes = new byte[fileNameUtf8Size];
                byteBuffer.get(fileNameUtf8Bytes, 0, fileNameUtf8Size);
                fileName = new String(fileNameUtf8Bytes, StandardCharsets.UTF_8);
                fileSize = byteBuffer.getLong();
            }
            case DATA -> {
                dataSize = byteBuffer.getInt();
                data = new byte[dataSize];
                byteBuffer.get(data, 0, dataSize);
            }
        }
    }

    public byte[] getBytes() {
        switch (type) {
            case INIT -> {
                return ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + fileNameUtf8Size + Long.BYTES)
                        .put(type.getByte())
                        .putInt(fileNameUtf8Size)
                        .put(fileName.getBytes(StandardCharsets.UTF_8))
                        .putLong(fileSize)
                        .array();
            }
            case DATA -> {
                return ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + dataSize)
                        .put(type.getByte())
                        .putInt(dataSize)
                        .put(data)
                        .array();
            }
            default -> {
                return ByteBuffer.allocate(Byte.BYTES).put(type.getByte()).array();
            }
        }
    }

    public static void sendFileTransferMessage(FileTransferMessage message, DataOutputStream outputStream) throws IOException {
        byte[] messageBytes = message.getBytes();
        outputStream.writeInt(messageBytes.length);
        outputStream.write(messageBytes);
        outputStream.flush();
    }

    public static FileTransferMessage receiveFileTransferMessage(DataInputStream inputStream) throws IOException {
        int messageSize = inputStream.readInt();
        byte[] messageBytes = new byte[messageSize];
        inputStream.read(messageBytes, 0, messageSize);
        return new FileTransferMessage(messageBytes);
    }

}
