package ru.nsu.fit.kolesnik.tcpfiletransferapp.cli;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.client.Client;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.exception.FilePathTooLongException;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.exception.FileTooBigException;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.server.Server;

@Command(name = "tcp-file-transfer", version = "tcp-file-transfer 1.0", mixinStandardHelpOptions = true)
public class TcpFileTransferCommand implements Runnable {

    private static final Logger logger = LogManager.getLogger(TcpFileTransferCommand.class);

    @Command(name = "server", description = "Initializes server")
    public void initializeServer(
            @Option(names = {"--port"}, description = "server port", paramLabel = "<port>", required = true) int port) {
        Server server = new Server(port);
        server.start();
    }

    @Command(name = "client", description = "Initializes client")
    public void initializeClient(
            @Option(names = {"--path"}, description = "file path", paramLabel = "<path>", required = true) String filePath,
            @Option(names = {"--hostname"}, description = "server hostname", paramLabel = "<hostname>", required = true)
            String serverHostname,
            @Option(names = {"--port"}, description = "server port", paramLabel = "<port>", required = true) int serverPort) {
        try {
            Client client = new Client(filePath, serverHostname, serverPort);
            client.start();
        } catch (FilePathTooLongException e) {
            logger.error("File path is too long!");
            throw new RuntimeException(e);
        } catch (FileTooBigException e) {
            logger.error("File is too big!");
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        System.out.println("TCP file transfer app!");
    }

}
