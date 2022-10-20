package ru.nsu.fit.kolesnik.tcpfiletransferapp.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.client.Client;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.server.Server;

@Command(name = "tcp-file-transfer", version = "tcp-file-transfer 1.0", mixinStandardHelpOptions = true)
public class TcpFileTransferCommand implements Runnable {

    @Command(name = "server", description = "Starts server")
    public void startServer(
            @Option(names = {"--port"}, description = "server port", paramLabel = "<port>", required = true) int port) {
        Server server = new Server(port);
        server.start();
    }

    @Command(name = "client", description = "Starts client")
    public void startClient(
            @Option(names = {"--path"}, description = "file path", paramLabel = "<path>", required = true)
            String filePath,
            @Option(names = {"--hostname"}, description = "server hostname", paramLabel = "<hostname>", required = true)
            String serverHostname,
            @Option(names = {"--port"}, description = "server port", paramLabel = "<port>", required = true)
            int serverPort) {
        Client client = new Client(filePath, serverHostname, serverPort);
        client.start();
    }

    @Override
    public void run() {
        System.out.println("TCP file transfer app!");
    }

}
