package io.ballerina.cli.task;

import io.ballerina.projects.Module;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectException;
import io.ballerina.projects.util.ProjectUtils;

import java.io.PrintStream;

import static io.ballerina.cli.launcher.LauncherUtils.createLauncherException;

public class RunTestExecutableTask implements Task {

    private final transient PrintStream out;

    public RunTestExecutableTask(PrintStream out) {
        this.out = out;
    }

    @Override
    public void execute(Project project) {
        out.println();
        out.println("Running tests");
        out.println();
        try {
            ProjectUtils.checkExecutePermission(project.sourceRoot());
        } catch (ProjectException e) {
            throw createLauncherException(e.getMessage());
        }
        this.runGeneratedTestExecutable(project);
    }

    private void runGeneratedTestExecutable(Project project) {

    }
}
