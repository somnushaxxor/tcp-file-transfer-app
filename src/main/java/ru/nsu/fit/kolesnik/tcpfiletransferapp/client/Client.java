package ru.nsu.fit.kolesnik.tcpfiletransferapp.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.exception.FilePathTooLongException;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.exception.FileTooBigException;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.protocol.FileTransferMessage;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.protocol.FileTransferMessageType;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static ru.nsu.fit.kolesnik.tcpfiletransferapp.protocol.FileTransferMessage.receiveFileTransferMessage;
import static ru.nsu.fit.kolesnik.tcpfiletransferapp.protocol.FileTransferMessage.sendFileTransferMessage;

public class Client {

    private static final int MAX_FILE_PATH_UTF8_LENGTH = 4096;
    private static final long MAX_FILE_SIZE = 1099511627776L;

    private static final Logger logger = LogManager.getLogger(Client.class);

    private final File outgoingFile;
    private final Socket socket;

    public Client(String filePath, String serverHostname, int serverPort) throws FilePathTooLongException, FileTooBigException {
        outgoingFile = new File(filePath);
        if (filePath.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_PATH_UTF8_LENGTH) {
            throw new FilePathTooLongException();
        }
        if (outgoingFile.length() > MAX_FILE_SIZE) {
            throw new FileTooBigException();
        }
        try {
            socket = new Socket(serverHostname, serverPort);
        } catch (IOException e) {
            logger.error("Could not connect to server " + serverHostname + ":" + serverPort + "!");
            throw new RuntimeException(e);
        }
        logger.info("Client created successfully");
    }

    public void start() {
        logger.info("Sending file to server");
        try (FileInputStream fileInputStream = new FileInputStream(outgoingFile);
             DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
             DataInputStream dataInputStream = new DataInputStream(socket.getInputStream())) {

            FileTransferMessage initializingMessage = new FileTransferMessage(FileTransferMessageType.INIT, outgoingFile.getName(),
                    outgoingFile.length());
            sendFileTransferMessage(initializingMessage, dataOutputStream);
            byte[] buffer = new byte[FileTransferMessage.MAX_DATA_SIZE];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer, 0, FileTransferMessage.MAX_DATA_SIZE)) != -1) {
                byte[] data = Arrays.copyOfRange(buffer, 0, bytesRead);
                FileTransferMessage dataMessage = new FileTransferMessage(FileTransferMessageType.DATA, data);
                sendFileTransferMessage(dataMessage, dataOutputStream);
            }
            FileTransferMessage finMessage = new FileTransferMessage(FileTransferMessageType.FIN);
            sendFileTransferMessage(finMessage, dataOutputStream);

            FileTransferMessage transferResultMessage = receiveFileTransferMessage(dataInputStream);
            switch (transferResultMessage.getType()) {
                case SUCCESS -> logger.info("File transfer succeeded");
                case FAILED -> logger.error("File transfer failed! Server did not receive whole file!");
            }
        } catch (IOException e) {
            logger.error("Error occurred while sending file!");
            shutdown();
            throw new RuntimeException(e);
        }
        shutdown();
    }

    private void shutdown() {
        logger.info("Closing connection with server");
        try {
            socket.close();
        } catch (IOException e) {
            logger.error("Failed to close client gracefully!");
            throw new RuntimeException(e);
        }
        logger.info("Connection closed");
    }

}
