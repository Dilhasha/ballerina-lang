package io.ballerina.cli.task;

import io.ballerina.cli.utils.FileUtils;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentConfig;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.JBallerinaBackend;
import io.ballerina.projects.JvmTarget;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleConfig;
import io.ballerina.projects.ModuleDependency;
import io.ballerina.projects.ModuleDescriptor;
import io.ballerina.projects.ModuleId;
import io.ballerina.projects.ModuleName;
import io.ballerina.projects.Package;
import io.ballerina.projects.PackageCompilation;
import io.ballerina.projects.PackageManifest;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectException;
import io.ballerina.projects.ProjectKind;
import io.ballerina.projects.internal.model.Target;
import org.ballerinalang.compiler.plugins.CompilerPlugin;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

import static io.ballerina.cli.launcher.LauncherUtils.createLauncherException;
import static io.ballerina.cli.utils.FileUtils.getFileNameWithoutExtension;
import static io.ballerina.projects.util.ProjectConstants.BLANG_COMPILED_JAR_EXT;
import static io.ballerina.projects.util.ProjectConstants.USER_DIR;

public class CreateTestExecutableTask implements Task {

    private final transient PrintStream out;
    private Path output;
    private Path currentDir;

    public CreateTestExecutableTask(PrintStream out, Path output) {
        this.out = out;
        this.output = output;
    }

    @Override
    public void execute(Project project) {
        this.out.println();
        this.out.println("Generating test executable");

        this.currentDir = Paths.get(System.getProperty(USER_DIR));
        Target target;

        try {
            if (project.kind().equals(ProjectKind.BUILD_PROJECT)) {
                target = new Target(project.sourceRoot());
            } else {
                target = new Target(Files.createTempDirectory("ballerina-cache" + System.nanoTime()));
                target.setOutputPath(getTestExecutablePath(project));
            }
        } catch (IOException e) {
            throw createLauncherException("unable to resolve target path:" + e.getMessage());
        } catch (ProjectException e) {
            throw createLauncherException("unable to create executable:" + e.getMessage());
        }

        Path executablePath;
        try {
            executablePath = target.getTestExecutablePath(project.currentPackage()).toAbsolutePath().normalize();
        } catch (IOException e) {
            throw createLauncherException(e.getMessage());
        }

        try {
            Module module = project.currentPackage().getDefaultModule();
            PackageCompilation pkgCompilation = project.currentPackage().getCompilation();
            JBallerinaBackend jBallerinaBackend = JBallerinaBackend.from(pkgCompilation, JvmTarget.JAVA_11);
            jBallerinaBackend.emitTestJar(executablePath, module.moduleName());

            // Print warnings for conflicted jars
            if (!jBallerinaBackend.conflictedJars().isEmpty()) {
                out.println("\twarning: Detected conflicting jar files:");
                for (JBallerinaBackend.JarConflict conflict : jBallerinaBackend.conflictedJars()) {
                    out.println(conflict.getWarning(project.buildOptions().listConflictedClasses()));
                }
            }
        } catch (ProjectException e) {
            throw createLauncherException(e.getMessage());
        }
        // notify plugin
        // todo following call has to be refactored after introducing new plugin architecture
        notifyPlugins(project, target);

        // Print the path of the executable
        Path relativePathToExecutable = currentDir.relativize(executablePath);
        if (relativePathToExecutable.toString().contains("..") ||
                relativePathToExecutable.toString().contains("." + File.separator)) {
            this.out.println("\t" + executablePath.toString());
        } else {
            this.out.println("\t" + relativePathToExecutable.toString());
        }
    }

    private void notifyPlugins(Project project, Target target) {
        ServiceLoader<CompilerPlugin> processorServiceLoader = ServiceLoader.load(CompilerPlugin.class);
        for (CompilerPlugin plugin : processorServiceLoader) {
            plugin.codeGenerated(project, target);
        }
    }

    private Path getTestExecutablePath(Project project) {

        Path fileName = project.sourceRoot().getFileName();

        // If the --output flag is not set, create the executable in the current directory
        if (this.output == null) {
            return this.currentDir.resolve(getFileNameWithoutExtension(fileName) + BLANG_COMPILED_JAR_EXT);
        }

        if (!this.output.isAbsolute()) {
            this.output = currentDir.resolve(this.output);
        }

        // If the --output is a directory create the executable in the given directory
        if (Files.isDirectory(this.output)) {
            return output.resolve(getFileNameWithoutExtension(fileName) + BLANG_COMPILED_JAR_EXT);
        }

        // If the --output does not have an extension, append the .jar extension
        if (!FileUtils.hasExtension(this.output)) {
            return Paths.get(this.output.toString() + BLANG_COMPILED_JAR_EXT);
        }

        return this.output;
    }
}
