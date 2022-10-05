package ru.nsu.fit.kolesnik.tcpfiletransferapp.protocol;

public enum FileTransferMessageType {
    INIT((byte) 0), DATA((byte) 1), FIN((byte) 2), SUCCESS((byte) 3), FAILED((byte) 4);

    private final byte code;

    FileTransferMessageType(byte code) {
        this.code = code;
    }

    private static final FileTransferMessageType[] types = new FileTransferMessageType[256];

    static {
        for (FileTransferMessageType type : values())
            types[type.getByte()] = type;
    }

    public byte getByte() {
        return code;
    }

    public static FileTransferMessageType getByCode(byte code) {
        return types[code];
    }
}
