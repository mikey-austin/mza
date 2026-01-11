package net.jackiemclean.mza;

import java.util.List;
import java.util.Map;

public interface CommandExecutor {
    void execute(String command, Map<String, String> env);

    List<String> executeAndGetOutput(String command, Map<String, String> env);
}
