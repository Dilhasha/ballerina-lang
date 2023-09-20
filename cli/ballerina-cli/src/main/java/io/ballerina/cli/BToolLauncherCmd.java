package io.ballerina.cli;

import io.ballerina.cli.launcher.LauncherUtils;
import io.ballerina.cli.launcher.util.BCompileUtil;
import picocli.CommandLine;

import java.io.IOException;

/**
 * {@code BLauncherCmd} represents a Ballerina launcher command.
 *
 * @since 0.8.0
 */
public interface BToolLauncherCmd {

    /**
     * Execute the command.
     */
    void execute();

    /**
     * Retrieve the command name.
     *
     * @return the name of the command
     */
    String getToolName();

    /**
     * Print the detailed description of the command.
     *
     * @param out a {@link StringBuilder} instance
     */
    void printLongDesc(StringBuilder out);

    /**
     * Print usgae info for the command.
     *
     * @param out a {@link StringBuilder} instance
     * @deprecated this method will be removed in a future version
     */
    @Deprecated
    void printUsage(StringBuilder out);

    /**
     * Set the parent {@link CommandLine} object to which commands are added as sub commands.
     *
     * @param parentCmdParser the parent {@link CommandLine} object
     */
    void setParentCmdParser(CommandLine parentCmdParser);

    /**
     * Retrieve command usage info.
     *
     * @param  commandName the name of the command
     * @return usage info for the specified command
     */
    static String getCommandUsageInfo(String commandName) {
        if (commandName == null) {
            throw LauncherUtils.createUsageExceptionWithHelp("invalid command");
        }

        String fileName = "cli-help/ballerina-" + commandName + ".help";
        try {
            return BCompileUtil.readFileAsString(fileName);
        } catch (IOException e) {
            throw LauncherUtils.createUsageExceptionWithHelp("usage info not available for command: " + commandName);
        }
    }
}
