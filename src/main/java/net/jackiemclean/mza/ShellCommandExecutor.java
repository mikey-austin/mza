package net.jackiemclean.mza;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShellCommandExecutor implements CommandExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(ShellCommandExecutor.class);

    @Override
    public void execute(String command, Map<String, String> env) {
        LOG.debug("Running -> {}", command);
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
            if (env != null) {
                pb.environment().putAll(env);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOG.debug("Command [{}] output: {}", command, line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Command failed with exit code " + exitCode + ": " + command);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute command: " + command, e);
        }
    }

    @Override
    public List<String> executeAndGetOutput(String command, Map<String, String> env) {
        LOG.debug("Running (capturing output) -> {}", command);
        List<String> output = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
            if (env != null) {
                pb.environment().putAll(env);
            }
            Process process = pb.start(); // Not merging error stream to keep stdout clean for parsing

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                // Read stderr for diagnostics
                try (BufferedReader stderrReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = stderrReader.readLine()) != null) {
                        LOG.error("Command [{}] error: {}", command, line);
                    }
                }
                throw new RuntimeException("Command failed with exit code " + exitCode + ": " + command);
            }
            return output;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute command: " + command, e);
        }
    }
}
