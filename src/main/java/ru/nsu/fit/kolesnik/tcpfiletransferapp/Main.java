package ru.nsu.fit.kolesnik.tcpfiletransferapp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import ru.nsu.fit.kolesnik.tcpfiletransferapp.cli.TcpFileTransferCommand;

public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        CommandLine commandLine = new CommandLine(new TcpFileTransferCommand());
        if (args.length == 0) {
            logger.error("No arguments passed!");
            commandLine.usage(System.out);
        } else {
            commandLine.execute(args);
        }
    }

}