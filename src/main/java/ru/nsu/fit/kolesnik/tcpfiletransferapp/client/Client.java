package ru.nsu.fit.kolesnik.tcpfiletransferapp.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private final File uploadingFile;
    private final Socket socket;

    public Client(String filePath, String serverHostname, int serverPort) {
        uploadingFile = new File(filePath);
        if (filePath.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_PATH_UTF8_LENGTH) {
            logger.error("File path is too long!");
            throw new IllegalArgumentException("File path is too long!");
        }
        if (uploadingFile.length() > MAX_FILE_SIZE) {
            logger.error("File is too big!");
            throw new IllegalArgumentException("File is too big!");
        }
        try {
            socket = new Socket(serverHostname, serverPort);
        } catch (IOException e) {
            logger.error("Could not connect to server {}:{}!", serverHostname, serverPort);
            throw new ClientException(
                    String.format("Could not connect to server %s:%s!", serverHostname, serverPort), e);
        }
        logger.info("Client created successfully");
    }

    public void start() {
        try (FileInputStream fileInputStream = new FileInputStream(uploadingFile);
             DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
             DataInputStream dataInputStream = new DataInputStream(socket.getInputStream())) {
            logger.info("Initializing file upload...");
            initializeFileUpload(dataOutputStream);
            logger.info("File upload initialized");
            logger.info("Uploading file to server...");
            boolean fileUploadedSuccessfully = uploadFile(fileInputStream, dataOutputStream, dataInputStream);
            logger.info("File upload finished");
            if (fileUploadedSuccessfully) {
                logger.info("File uploaded successfully");
            } else {
                logger.error("File upload failed! Server did not receive whole file!");
            }
        } catch (IOException e) {
            logger.error("Error occurred while starting client!");
            shutdown();
            throw new ClientException("Error occurred while starting client!", e);
        }
        shutdown();
    }

    private void initializeFileUpload(DataOutputStream dataOutputStream) {
        FileTransferMessage transferInitializingMessage = new FileTransferMessage(FileTransferMessageType.INIT,
                uploadingFile.getName().getBytes(StandardCharsets.UTF_8).length, uploadingFile.getName(),
                uploadingFile.length());
        try {
            sendFileTransferMessage(transferInitializingMessage, dataOutputStream);
        } catch (IOException e) {
            logger.error("Error occurred while initializing file upload!");
            shutdown();
            throw new ClientException("Error occurred while initializing file upload!", e);
        }
    }

    private boolean uploadFile(FileInputStream fileInputStream, DataOutputStream dataOutputStream,
                               DataInputStream dataInputStream) {
        try {
            byte[] buffer = new byte[FileTransferMessage.MAX_DATA_SIZE];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer, 0, FileTransferMessage.MAX_DATA_SIZE)) != -1) {
                byte[] data = Arrays.copyOfRange(buffer, 0, bytesRead);
                FileTransferMessage dataMessage = new FileTransferMessage(FileTransferMessageType.DATA, data.length,
                        data);
                sendFileTransferMessage(dataMessage, dataOutputStream);
            }

            FileTransferMessage transferFinalizingMessage = new FileTransferMessage(FileTransferMessageType.FIN);
            sendFileTransferMessage(transferFinalizingMessage, dataOutputStream);

            FileTransferMessage transferResultMessage = receiveFileTransferMessage(dataInputStream);

            return transferResultMessage.getType() == FileTransferMessageType.SUCCESS;
        } catch (IOException e) {
            logger.error("Error occurred while uploading file!");
            shutdown();
            throw new ClientException("Error occurred while uploading file!", e);
        }
    }

    private void shutdown() {
        logger.info("Shutting client down");
        try {
            socket.close();
        } catch (IOException e) {
            logger.error("Failed to shutdown client gracefully!");
            throw new ClientException("Failed to shutdown client gracefully!", e);
        }
        logger.info("Client shutdown");
    }

}
