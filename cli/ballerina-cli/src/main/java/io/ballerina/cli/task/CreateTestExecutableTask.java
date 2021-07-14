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
            // Temp fix- Change the compilation for test execution
//            Package currentPackage = project.currentPackage();
//            Module module = currentPackage.getDefaultModule();
//            for (DocumentId testDocId:module.testDocumentIds()) {
//                DocumentConfig documentConfig = generateDocument(currentPackage.project(), module,module.document(testDocId));
//                module = module.modify().removeDocument(testDocId).apply();
//                module = module.modify().addDocument(documentConfig).apply();
//            }
//            for (DocumentId docId:module.documentIds()) {
//                Document doc = module.document(docId);
//                if(doc.name().equals("main.bal")){
//                    DocumentConfig documentConfig = generateMainDocument(currentPackage.project(), module,doc);
//                    module = module.modify().removeDocument(docId).apply();
//                    module = module.modify().addDocument(documentConfig).apply();
//                }
//            }
//            List<DocumentConfig> srcDocs = new ArrayList<>();
//            List<DocumentConfig> testDocs = new ArrayList<>();
//            for (DocumentId docId:module.documentIds()) {
//                srcDocs.add(DocumentConfig.from(docId,
//                        module.document(docId).textDocument().toString(),module.document(docId).name()));
//            }
//            for (DocumentId docId:module.testDocumentIds()) {
//                testDocs.add(DocumentConfig.from(docId,
//                        module.document(docId).textDocument().toString(),module.document(docId).name()));
//            }
//            PackageManifest pkgManifest = currentPackage.manifest();
//            List<ModuleDescriptor> descriptors = new ArrayList<>();
//            for (Module moduleIns: currentPackage.modules()) {
//                ModuleDescriptor moduleDesc = ModuleDescriptor.from(moduleIns.moduleName(), pkgManifest.descriptor());
//                descriptors.add(moduleDesc);
//            }
//            ModuleConfig newModuleConfig = ModuleConfig.from(module.moduleId(), module.descriptor(), srcDocs,
//                    testDocs, null, descriptors);
//            Package packageIns = currentPackage.modify().addModule(newModuleConfig).apply();
//            PackageCompilation pkgCompilation = packageIns.getCompilation();
            Module module = project.currentPackage().getDefaultModule();
            PackageCompilation pkgCompilation = project.currentPackage().getCompilation();
            JBallerinaBackend jBallerinaBackend = JBallerinaBackend.from(pkgCompilation, JvmTarget.JAVA_11);
            jBallerinaBackend.emitTestJar(JBallerinaBackend.OutputType.EXEC, executablePath, module.moduleName());

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

    private DocumentConfig generateDocument(Project project, Module module, Document document) {
        String newFileContent = document.textDocument().toString();
        String newFilePath = project.sourceRoot().toString() + "/" +  document.name();
        if(document.name().contains("/")){
            newFilePath = project.sourceRoot().toString() + "/" +  document.name().split("/")[1];
        }
        DocumentId newDocumentId = DocumentId.create(newFilePath, module.moduleId());
        return DocumentConfig.from(newDocumentId, newFileContent, document.name());
    }

    private DocumentConfig generateMainDocument(Project project, Module module, Document document) {
        String newFileContent = "import ballerina/test;\n" +
                "\n" +
                "public function main() returns error? {\n" +
                "    () v = check testExecute();\n" +
                "}\n" +
                "\n" +
                "function getTests() returns map<function> {\n" +
                "    function f1 = function() {\n" +
                "        test:assertTrue(false);\n" +
                "    };\n" +
                "    function f2 = function() {\n" +
                "        test:assertEquals(\"apple\", \"apple\");\n" +
                "    };\n" +
                "    function f3 = function() {\n" +
                "        test:assertNotEquals(false, true);\n" +
                "    };\n" +
                "    return {\"f1\": f1,\"f2\":f2,\"f3\":f3};\n" +
                "}\n" +
                "\n" +
                "\n";
        Path filePath = project.sourceRoot().resolve(document.name());
        DocumentId newDocumentId = DocumentId.create(filePath.toString(), module.moduleId());
        return DocumentConfig.from(newDocumentId, newFileContent, document.name());
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
