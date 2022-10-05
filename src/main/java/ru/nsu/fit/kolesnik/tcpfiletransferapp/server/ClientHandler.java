package ru.nsu.fit.kolesnik.tcpfiletransferapp.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.protocol.FileTransferMessage;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.protocol.FileTransferMessageType;

import java.io.*;
import java.net.Socket;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static ru.nsu.fit.kolesnik.tcpfiletransferapp.protocol.FileTransferMessage.receiveFileTransferMessage;
import static ru.nsu.fit.kolesnik.tcpfiletransferapp.protocol.FileTransferMessage.sendFileTransferMessage;

public class ClientHandler implements Runnable {

    private static final long SPEED_COUNT_PERIOD = 3;

    private static final Logger logger = LogManager.getLogger(ClientHandler.class);

    private final Socket socket;
    private final ScheduledExecutorService scheduledExecutorService;
    private final Lock lock;
    private long bytesReceivedWithinPeriod;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        lock = new ReentrantLock();
        bytesReceivedWithinPeriod = 0;
    }

    @Override
    public void run() {
        scheduledExecutorService.scheduleAtFixedRate(this::countFileReceivingSpeed, SPEED_COUNT_PERIOD, SPEED_COUNT_PERIOD,
                TimeUnit.SECONDS);
        receiveFile();
    }

    private void receiveFile() {
        logger.info("Receiving file from " + socket.getInetAddress().getHostAddress());
        Instant start = Instant.now();
        try (DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
             DataInputStream dataInputStream = new DataInputStream(socket.getInputStream())) {

            FileTransferMessage initializingMessage = receiveFileTransferMessage(dataInputStream);
            File file = createFile(initializingMessage.getFileName());

            FileOutputStream fileOutputStream = new FileOutputStream(file);

            long totalBytesReceived = 0;
            boolean receiving = true;
            while (receiving) {
                FileTransferMessage message = receiveFileTransferMessage(dataInputStream);
                switch (message.getType()) {
                    case DATA -> {
                        fileOutputStream.write(message.getData());
                        totalBytesReceived += message.getDataSize();
                        lock.lock();
                        bytesReceivedWithinPeriod += message.getDataSize();
                        lock.unlock();
                    }
                    case FIN -> receiving = false;
                }
            }
            scheduledExecutorService.shutdown();
            if (initializingMessage.getFileSize() == totalBytesReceived) {
                logger.info("File transfer succeeded");
                Instant end = Instant.now();
                logger.info("Average file receiving speed: "
                        + totalBytesReceived * 1000 / (end.toEpochMilli() - start.toEpochMilli()) + " bytes/s");
                sendFileTransferMessage(new FileTransferMessage(FileTransferMessageType.SUCCESS), dataOutputStream);
            } else {
                logger.error("File transfer failed! Server did not receive whole file!");
                sendFileTransferMessage(new FileTransferMessage(FileTransferMessageType.FAILED), dataOutputStream);
            }
            fileOutputStream.close();
        } catch (IOException e) {
            scheduledExecutorService.shutdown();
            shutdown();
            logger.error("Failed to receive file from " + socket.getInetAddress().getHostAddress());
            e.printStackTrace();
        }
        shutdown();
    }

    private File createFile(String fileName) {
        File file;
        boolean fileCreated;
        String prefix = "";
        int i = 0;
        do {
            file = new File("./" + Server.UPLOADS_DIRECTORY_NAME + "/" + prefix + fileName);
            try {
                fileCreated = file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (i > 0) {
                prefix = "copy" + i + "_";
            } else {
                prefix = "copy_";
            }
            i++;
        } while (!fileCreated);
        return file;
    }

    private void countFileReceivingSpeed() {
        lock.lock();
        logger.info("Current file receiving speed: " + bytesReceivedWithinPeriod / SPEED_COUNT_PERIOD + " bytes/s");
        bytesReceivedWithinPeriod = 0;
        lock.unlock();
    }

    private void shutdown() {
        logger.info("Closing connection with " + socket.getInetAddress().getHostAddress());
        try {
            socket.close();
        } catch (IOException e) {
            logger.error("Failed to close connection with " + socket.getInetAddress().getHostAddress() + " gracefully!");
            throw new RuntimeException(e);
        }
        logger.info("Connection closed");
    }

}
