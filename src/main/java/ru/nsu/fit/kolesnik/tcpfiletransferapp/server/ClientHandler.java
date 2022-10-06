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
    private File downloadingFile;
    private long bytesReceivedWithinPeriod;
    private long totalBytesReceived;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        lock = new ReentrantLock();
        bytesReceivedWithinPeriod = 0;
        totalBytesReceived = 0;
    }

    @Override
    public void run() {
        try (DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
             DataInputStream dataInputStream = new DataInputStream(socket.getInputStream())) {
            logger.info("Initializing file download from " + socket.getInetAddress().getHostAddress() + "...");
            long requiredBytesNumber = initializeFileDownload(dataInputStream);
            logger.info("File download initialized");
            scheduledExecutorService.scheduleAtFixedRate(this::countFileDownloadSpeed, SPEED_COUNT_PERIOD,
                    SPEED_COUNT_PERIOD, TimeUnit.SECONDS);
            logger.info("Downloading file from " + socket.getInetAddress().getHostAddress() + "...");
            Instant begin = Instant.now();
            boolean fileDownloadedSuccessfully = downloadFile(requiredBytesNumber, dataOutputStream, dataInputStream);
            Instant end = Instant.now();
            logger.info("File download finished");
            scheduledExecutorService.shutdown();
            if (fileDownloadedSuccessfully) {
                logger.info("File downloaded successfully");
            } else {
                logger.error("File download failed! Server did not receive whole file!");
            }
            printAverageDownloadSpeed(begin, end);
        } catch (IOException e) {
            logger.error("Error occurred while handling client " + socket.getInetAddress().getHostAddress() + "!");
            shutdown();
            throw new RuntimeException(e);
        }
        shutdown();
    }

    private long initializeFileDownload(DataInputStream dataInputStream) {
        FileTransferMessage initializingMessage;
        try {
            initializingMessage = receiveFileTransferMessage(dataInputStream);
        } catch (IOException e) {
            logger.error("Error occurred while initializing file download!");
            shutdown();
            throw new RuntimeException(e);
        }
        downloadingFile = createDownloadingFile(initializingMessage.getFileName());
        return initializingMessage.getFileSize();
    }

    private File createDownloadingFile(String fileName) {
        File file;
        boolean fileCreated;
        String prefix = "";
        int i = 0;
        do {
            file = new File("./" + Server.UPLOADS_DIRECTORY_NAME + "/" + prefix + fileName);
            try {
                fileCreated = file.createNewFile();
            } catch (IOException e) {
                shutdown();
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

    private boolean downloadFile(long requiredBytesNumber, DataOutputStream dataOutputStream,
                                 DataInputStream dataInputStream) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(downloadingFile)) {
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
            if (requiredBytesNumber == totalBytesReceived) {
                sendFileTransferMessage(new FileTransferMessage(FileTransferMessageType.SUCCESS), dataOutputStream);
                return true;
            } else {
                sendFileTransferMessage(new FileTransferMessage(FileTransferMessageType.FAILED), dataOutputStream);
                return false;
            }
        } catch (IOException e) {
            logger.error("Failed to download file from " + socket.getInetAddress().getHostAddress() + "!");
            shutdown();
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            logger.error("Server internal error occurred!");
            logger.error("Failed to download file from " + socket.getInetAddress().getHostAddress() + "!");
            shutdown();
            throw e;
        }
    }

    private void countFileDownloadSpeed() {
        lock.lock();
        logger.info("Current file download speed: " + bytesReceivedWithinPeriod / SPEED_COUNT_PERIOD + " bytes/s");
        bytesReceivedWithinPeriod = 0;
        lock.unlock();
    }

    private void printAverageDownloadSpeed(Instant begin, Instant end) {
        logger.info("Average file download speed: "
                + totalBytesReceived * 1000 / (end.toEpochMilli() - begin.toEpochMilli()) + " bytes/s");
    }

    private void shutdown() {
        scheduledExecutorService.shutdown();
        logger.info("Shutting connection with " + socket.getInetAddress().getHostAddress() + " down");
        try {
            socket.close();
        } catch (IOException e) {
            logger.error("Failed to shutdown connection with " + socket.getInetAddress().getHostAddress()
                    + " gracefully!");
            throw new RuntimeException(e);
        }
        logger.info("Connection with " + socket.getInetAddress().getHostAddress() + " shutdown");
    }

}
