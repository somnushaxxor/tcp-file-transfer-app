package ru.nsu.fit.kolesnik.tcpfiletransferapp.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.server.handler.ClientHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    public static final String UPLOADS_DIRECTORY_NAME = "uploads";

    private static final Logger logger = LogManager.getLogger(Server.class);

    private final ServerSocket serverSocket;
    private final ExecutorService threadPool;


    public Server(int port) {
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            logger.error("Could not create server!");
            throw new ServerException("Could not create server!", e);
        }
        threadPool = Executors.newCachedThreadPool();
        createUploadsDirectory();
        logger.info("Server created successfully");
    }

    public void start() {
        logger.info("Server started");
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                logger.info("New connection accepted");
                threadPool.execute(new ClientHandler(socket));
            } catch (IOException e) {
                logger.error("Error occurred while waiting for new connection!");
                shutdown();
                throw new ServerException("Error occurred while waiting for new connection!", e);
            }
        }
        shutdown();
    }

    private void createUploadsDirectory() {
        Path uploadsPath = Path.of("./", UPLOADS_DIRECTORY_NAME);
        try {
            if (!Files.isDirectory(uploadsPath)) {
                Files.createDirectory(uploadsPath);
                logger.info("Uploads directory created successfully");
            }
        } catch (IOException e) {
            logger.error("Error occurred while creating uploads directory!");
            shutdown();
            throw new ServerException("Error occurred while creating uploads directory!", e);
        }
    }

    private void shutdown() {
        logger.info("Shutting server down");
        threadPool.shutdown();
        try {
            if (!serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Failed to shutdown server gracefully!");
            throw new ServerException("Failed to shutdown server gracefully!", e);
        }
        logger.info("Server shutdown");
    }

}
