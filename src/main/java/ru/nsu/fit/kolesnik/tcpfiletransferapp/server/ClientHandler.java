package ru.nsu.fit.kolesnik.tcpfiletransferapp.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.message.FileTransferMessage;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.message.FileTransferMessageType;

import java.io.*;
import java.net.Socket;

import static ru.nsu.fit.kolesnik.tcpfiletransferapp.message.FileTransferMessage.receiveFileTransferMessage;
import static ru.nsu.fit.kolesnik.tcpfiletransferapp.message.FileTransferMessage.sendFileTransferMessage;

public class ClientHandler implements Runnable {

    private final Logger logger = LogManager.getLogger(ClientHandler.class);

    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        receiveFile();
    }

    private void receiveFile() {
        logger.info("Receiving file from " + socket.getInetAddress().getHostAddress());
        File file = null;
        try (DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
             DataInputStream dataInputStream = new DataInputStream(socket.getInputStream())) {

            FileTransferMessage initializingMessage = receiveFileTransferMessage(dataInputStream);
            file = createFile(initializingMessage.getFileName());

            FileOutputStream fileOutputStream = new FileOutputStream(file);

            long totalBytesReceived = 0;
            boolean receiving = true;
            while (receiving) {
                FileTransferMessage message = receiveFileTransferMessage(dataInputStream);
                switch (message.getType()) {
                    case DATA -> {
                        fileOutputStream.write(message.getData());
                        totalBytesReceived += message.getDataSize();
                    }
                    case FIN -> receiving = false;
                }
            }
            if (initializingMessage.getFileSize() == totalBytesReceived) {
                logger.info("File transfer succeeded");
                sendFileTransferMessage(new FileTransferMessage(FileTransferMessageType.SUCCESS), dataOutputStream);
            } else {
                logger.error("File transfer failed! Server did not receive whole file!");
                sendFileTransferMessage(new FileTransferMessage(FileTransferMessageType.FAILED), dataOutputStream);
                file.delete();
                logger.info("File \"" + file.getName() + "\" deleted");
            }
            fileOutputStream.close();
        } catch (IOException e) {
            shutdown();
            logger.error("Failed to receive file from " + socket.getInetAddress().getHostAddress());
            if (file != null) {
                file.delete();
                logger.info("File \"" + file.getName() + "\" deleted");
            }
            e.printStackTrace();
        }
    }

    private File createFile(String fileName) {
        String[] fileNameParts = fileName.split("\\.");
        File file;
        boolean fileCreated;
        String postfix = "";
        int i = 0;
        do {
            file = new File("./" + Server.UPLOADS_DIRECTORY_NAME + "/" + fileNameParts[0] + postfix + "."
                    + fileNameParts[1]);
            try {
                fileCreated = file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            i++;
            postfix = String.valueOf(i);
        } while (!fileCreated);
        return file;
    }

    private void shutdown() {
        try {
            socket.close();
        } catch (IOException e) {
            logger.error("Failed to shutdown connection with " + socket.getInetAddress().getHostAddress() + " gracefully!");
            throw new RuntimeException(e);
        }
    }

}
