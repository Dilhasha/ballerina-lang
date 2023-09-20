/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.cli.task;

import io.ballerina.cli.BLauncherCmd;
import io.ballerina.cli.launcher.BallerinaCliCommands;
import io.ballerina.cli.launcher.CustomToolClassLoader;
import io.ballerina.cli.launcher.util.BalToolsUtil;
import io.ballerina.projects.Project;
import picocli.CommandLine;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

import static io.ballerina.projects.PackageManifest.Tool;

/**
 * Resolve maven dependencies.
 */
public class RunBalToolsTask implements Task {
    private final transient PrintStream out;

    public RunBalToolsTask(PrintStream out) {
        this.out = out;
    }

    @Override
    public void execute(Project project) {
        List<Tool> tools = project.currentPackage().manifest().tools();

        Map<String, Object> options = new HashMap<>();
        options.put("mode", "client");
        Tool tool = new Tool("openapi", "petstore-client", "/Users/dilhashanazeer/Downloads/petstore.yaml", "test", options);
        tools.add(tool);

        try {
            DefaultToolCmd defaultCmd = new DefaultToolCmd();
            CommandLine cmdParser = new CommandLine(defaultCmd);
            defaultCmd.setParentCmdParser(cmdParser);

            String commandName = tool.getType();
            ServiceLoader<BLauncherCmd> cmds = ServiceLoader.load(BLauncherCmd.class);

            BLauncherCmd targetCmd = null; // This is the command that matches tool.getType()

            for (BLauncherCmd bCmd : cmds) {
                if (bCmd.getName().equals(commandName)) {
                    targetCmd = bCmd;
                    break; // No need to continue the loop if we found the desired command
                }
            }

            if (targetCmd != null) {
                cmdParser.addSubcommand(targetCmd.getName(), targetCmd);
                cmdParser.setDefaultValueProvider(new SimpleDefaultProvider(tool));
                targetCmd.setParentCmdParser(cmdParser);

                String[] args = {commandName, "/Users/dilhashanazeer/Downloads/petstore.yaml"}; // use the commandName as the argument
                List<CommandLine> parsedCommands = cmdParser.parse(args);

                BLauncherCmd cmd = parsedCommands.get(parsedCommands.size() - 1).getCommand();
                cmd.execute();
            } else {
                out.println("Command not found: " + commandName);
            }

            System.out.println("tools: " + tools);

        } catch (Throwable e) {
            out.println(e.getMessage());
        }

//        }

    }


    @CommandLine.Command(description = "Default Tool Command.", name = "default")
    public static class DefaultToolCmd implements BLauncherCmd {


        @CommandLine.Option(names = "source", description = "start Ballerina in remote debugging mode")
        private String source;

        @CommandLine.Option(names = "mode", description = "start Ballerina in remote debugging mode")
        private String mode;

        @CommandLine.Parameters(arity = "0..1")
        private List<String> argList = new ArrayList<>();

        private CommandLine parentCmdParser;

        @Override
        public void execute() {
            //need to be overriden
        }

        @Override
        public String getName() {
            return BallerinaCliCommands.DEFAULT;
        }

        @Override
        public void printLongDesc(StringBuilder out) {

        }

        @Override
        public void printUsage(StringBuilder out) {
        }

        @Override
        public void setParentCmdParser(CommandLine parentCmdParser) {
            this.parentCmdParser = parentCmdParser;
        }
    }

}



class SimpleDefaultProvider implements CommandLine.IDefaultValueProvider {

    Tool tool;
    SimpleDefaultProvider(Tool tool) {
        this.tool = tool;
    }

    @Override
    public String defaultValue(CommandLine.Model.ArgSpec argSpec) throws Exception {

        if (argSpec.isOption()) {
            CommandLine.Model.OptionSpec option = (CommandLine.Model.OptionSpec) argSpec;
            Map<String, Object> ops = tool.getOptions();
            for (String name : ops.keySet()) {
                if (("--" + name).equals(option.longestName())) {
                    return ops.get(name).toString();
                }
            }
            if ("--input".equals(option.longestName())) {
                return "true";
            }
        }
        return null;
    }

}