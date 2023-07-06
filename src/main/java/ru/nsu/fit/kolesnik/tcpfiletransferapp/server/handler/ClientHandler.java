package ru.nsu.fit.kolesnik.tcpfiletransferapp.server.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.protocol.FileTransferMessage;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.protocol.FileTransferMessageType;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.server.Server;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
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
            logger.info("Initializing file download from {}...", socket.getInetAddress().getHostAddress());
            long requiredBytesNumber = initializeFileDownload(dataInputStream);
            logger.info("File download initialized");
            scheduledExecutorService.scheduleAtFixedRate(this::printCurrentFileDownloadSpeed, SPEED_COUNT_PERIOD,
                    SPEED_COUNT_PERIOD, TimeUnit.SECONDS);
            logger.info("Downloading file from {}...", socket.getInetAddress().getHostAddress());
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
            logger.error("Error occurred while handling client {}!", socket.getInetAddress().getHostAddress());
            shutdown();
            throw new ClientHandlerException(
                    String.format("Error occurred while handling client %s!", socket.getInetAddress().getHostAddress()),
                    e
            );
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
            throw new ClientHandlerException("Error occurred while initializing file download!", e);
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
                logger.error("Error occurred while creating download file!");
                shutdown();
                throw new ClientHandlerException("Error occurred while creating downloading file!", e);
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

    private void deleteDownloadingFile() {
        try {
            Files.delete(downloadingFile.toPath());
        } catch (IOException e) {
            logger.error("Error occurred while deleting redundant downloading file!");
            throw new ClientHandlerException("Error occurred while deleting redundant downloading file!", e);
        }
    }

    private boolean downloadFile(long requiredBytesNumber, DataOutputStream dataOutputStream,
                                 DataInputStream dataInputStream) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(downloadingFile)) {
            boolean receiving = true;
            while (receiving) {
                FileTransferMessage message = receiveFileTransferMessage(dataInputStream);
                if (message.getType() == FileTransferMessageType.DATA) {
                    fileOutputStream.write(message.getData());
                    totalBytesReceived += message.getDataSize();
                    lock.lock();
                    bytesReceivedWithinPeriod += message.getDataSize();
                    lock.unlock();
                } else if (message.getType() == FileTransferMessageType.FIN) {
                    receiving = false;
                }
            }
            if (requiredBytesNumber == totalBytesReceived) {
                sendFileTransferMessage(new FileTransferMessage(FileTransferMessageType.SUCCESS), dataOutputStream);
                return true;
            } else {
                deleteDownloadingFile();
                sendFileTransferMessage(new FileTransferMessage(FileTransferMessageType.FAILED), dataOutputStream);
                return false;
            }
        } catch (IOException e) {
            logger.error("Failed to download file from {}!", socket.getInetAddress().getHostAddress());
            deleteDownloadingFile();
            shutdown();
            throw new ClientHandlerException(
                    String.format("Failed to download file from %s!", socket.getInetAddress().getHostAddress()), e);
        }
    }

    private void printCurrentFileDownloadSpeed() {
        lock.lock();
        logger.info("Current file download speed: {} bytes/s", bytesReceivedWithinPeriod / SPEED_COUNT_PERIOD);
        bytesReceivedWithinPeriod = 0;
        lock.unlock();
    }

    private void printAverageDownloadSpeed(Instant begin, Instant end) {
        long averageDownloadSpeed = totalBytesReceived * 1000 / (end.toEpochMilli() - begin.toEpochMilli());
        logger.info("Average file download speed: {} bytes/s", averageDownloadSpeed);
    }

    private void shutdown() {
        scheduledExecutorService.shutdown();
        logger.info("Shutting connection with {} down", socket.getInetAddress().getHostAddress());
        try {
            socket.close();
        } catch (IOException e) {
            logger.error("Failed to shutdown connection with {} gracefully!",
                    socket.getInetAddress().getHostAddress());
            throw new ClientHandlerException("Failed to shutdown connection with {} gracefully!", e);
        }
        logger.info("Connection with {} shutdown", socket.getInetAddress().getHostAddress());
    }

}
