/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.projects.util;

import io.ballerina.projects.JBallerinaBackend;
import io.ballerina.projects.JarLibrary;
import io.ballerina.projects.JarResolver;
import io.ballerina.projects.JvmTarget;
import io.ballerina.projects.Module;
import io.ballerina.projects.Package;
import io.ballerina.projects.PackageCompilation;
import io.ballerina.projects.PackageManifest;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectException;
import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.internal.configurable.providers.toml.TomlDetails;
import io.ballerina.runtime.internal.launch.LaunchUtils;
import io.ballerina.runtime.internal.scheduling.Scheduler;
import io.ballerina.runtime.internal.scheduling.Strand;
import io.ballerina.runtime.internal.values.FutureValue;
import org.wso2.ballerinalang.compiler.bir.codegen.JvmCodeGenUtil;
import org.wso2.ballerinalang.compiler.desugar.ASTBuilderUtil;
import org.wso2.ballerinalang.compiler.tree.BLangIdentifier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Function;

import static org.wso2.ballerinalang.compiler.util.ProjectDirConstants.USER_DIR;

/**
 * Utility methods for run Ballerina functions.
 *
 * @since 2.0.0
 */
public class BRunUtil {

    private static final String MODULE_INIT_CLASS_NAME = "$_init";
    private static final String MAIN_CLASS_NAME = "main";
    private static final String CONFIGURATION_CLASS_NAME = "$configurationMapper";

    public static Class<?> mainClazz;
    public static Class<?> configClazz;
    public static Class<?> initClazz;
    public static boolean classLoaded = false;


    public static void runGeneratedExecutable(Project project, List<String> args, PrintStream err) {
        Module executableModule = project.currentPackage().getDefaultModule();
        long start = System.currentTimeMillis();
        PackageCompilation packageCompilation = project.currentPackage().getCompilation();
        System.out.println("packageCompilationDuration: " + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();
        JBallerinaBackend jBallerinaBackend = JBallerinaBackend.from(packageCompilation, JvmTarget.JAVA_11);
        System.out.println("codeGenDuration: " + (System.currentTimeMillis() - start));
        JarResolver jarResolver = jBallerinaBackend.jarResolver();

        start = System.currentTimeMillis();
        String initClassName = JarResolver.getQualifiedClassName(
                executableModule.packageInstance().packageOrg().toString(),
                executableModule.packageInstance().packageName().toString(),
                executableModule.packageInstance().packageVersion().toString(),
                MODULE_INIT_CLASS_NAME);
        try {
            List<String> commands = new ArrayList<>();
            commands.add(System.getProperty("java.command"));
            commands.add("-XX:+HeapDumpOnOutOfMemoryError");
            commands.add("-XX:HeapDumpPath=" + System.getProperty(USER_DIR));
            // Sets classpath with executable thin jar and all dependency jar paths.
            commands.add("-cp");
            commands.add(getAllClassPaths(jarResolver));
            commands.add(initClassName);
            commands.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(commands).inheritIO();
            Process process = pb.start();
            process.waitFor();
            int exitValue = process.exitValue();
            if (exitValue != 0) {
                throw new ProjectException("Error occurred");
            }
        } catch (IOException | InterruptedException e) {
            throw new ProjectException("Error occurred while running the executable ", e.getCause());
        }
        System.out.println("api process execution time: " + (System.currentTimeMillis() - start));
    }

    private static String getAllClassPaths(JarResolver jarResolver) {
        StringJoiner cp = new StringJoiner(File.pathSeparator);
        jarResolver.getJarFilePathsRequiredForExecution().stream()
                .map(JarLibrary::path).map(Path::toString)
                .forEach(cp::add);
        return cp.toString();
    }

    public static void run(Project project, List<String> changedFileList) {
        Package currentPackage = project.currentPackage();
        ClassLoader classLoader = null;
        if (!changedFileList.isEmpty() || !classLoaded) {
            //Compile
            long start = System.currentTimeMillis();
            currentPackage.getResolution();
            System.out.println("packageResolutionDuration: " + (System.currentTimeMillis() - start));
            start = System.currentTimeMillis();
            PackageCompilation packageCompilation = currentPackage.getCompilation();
            System.out.println("packageCompilationDuration: " + (System.currentTimeMillis() - start));
            if (packageCompilation.diagnosticResult().hasErrors()) {
                ContentServer.getInstance().sendMessage("Compilation failed with errors: " +
                        currentPackage.project().sourceRoot());
                return;
            }
            //Codegen
            start = System.currentTimeMillis();
            JBallerinaBackend jBallerinaBackend = JBallerinaBackend.from(packageCompilation, JvmTarget.JAVA_11);
            System.out.println("codeGenDuration: " + (System.currentTimeMillis() - start));
            if(jBallerinaBackend.diagnosticResult().hasErrors()){
                ContentServer.getInstance().sendMessage("Code generation failed with errors: " +
                        currentPackage.project().sourceRoot());
                return;
            }
            classLoader = jBallerinaBackend.jarResolver().getClassLoaderWithRequiredJarFilesForExecution();
            if (classLoader != null) {
                updateClassLoaders(classLoader, currentPackage.manifest());
                executeMain();
            }
        } else {
            // Skip class loading if there are no changes and classes are already loaded
            executeMain();
        }
    }

    private static void updateClassLoaders(ClassLoader classLoader, PackageManifest packageManifest) {
        String org = packageManifest.org().toString();
        String module = packageManifest.name().toString();
        String version = packageManifest.version().toString();
        String initClassName = JarResolver.getQualifiedClassName(org, module, version, MODULE_INIT_CLASS_NAME);
        String mainClassName = JarResolver.getQualifiedClassName(org, module, version, MAIN_CLASS_NAME);
        String configClassName = JarResolver.getQualifiedClassName(org, module, version, CONFIGURATION_CLASS_NAME);
        try {
            initClazz = classLoader.loadClass(initClassName);
            mainClazz = classLoader.loadClass(mainClassName);
            configClazz = classLoader.loadClass(configClassName);
        } catch (ClassNotFoundException e) {
            ContentServer.getInstance().sendMessage("Error while loading classes for execution. " +  e.getMessage());
            return;
        }
        classLoaded = true;
    }

    private static void executeMain(){
        long start = System.currentTimeMillis();
        final Scheduler scheduler = new Scheduler(false);
        TomlDetails configurationDetails = LaunchUtils.getConfigurationDetails();
        directRun(configClazz, "$configureInit",
                new Class[]{String[].class, Path[].class, String.class}, new Object[]{new String[]{},
                        configurationDetails.paths, configurationDetails.configContent});
        LaunchUtils.startListeners(false);
        try {
            runOnSchedule(initClazz, ASTBuilderUtil.createIdentifier(null, "$moduleInit"), scheduler);
            runOnSchedule(mainClazz, ASTBuilderUtil.createIdentifier(null, "main"), scheduler);
            runOnSchedule(initClazz, ASTBuilderUtil.createIdentifier(null, "$moduleStart"), scheduler);
            System.out.println("ProgramExecutionDuration: " + (System.currentTimeMillis() - start));
        } catch (Exception exception) {
            ContentServer.getInstance().sendMessage("Execption occurred while running the program " + exception.getMessage());
        }
    }

    private static void runOnSchedule(Class<?> initClazz, BLangIdentifier name, Scheduler scheduler) {
        String funcName = JvmCodeGenUtil.cleanupFunctionName(name.value);
        try {
            final Method method = initClazz.getDeclaredMethod(funcName, Strand.class);
            // Create a stream to hold the output
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(byteArrayOutputStream);
            PrintStream existingStream = System.out;
            // Use defined stream
            System.setOut(printStream);
            //TODO fix following method invoke to scheduler.schedule()
            Function<Object[], Object> func = objects -> {
                try {
                    Object response = method.invoke(null, objects[0]);
                    System.out.flush();
                    System.setOut(existingStream);
                    return response;
                } catch (InvocationTargetException e) {
                    Throwable targetException = e.getTargetException();
                    ContentServer.getInstance().sendMessage("Error occurred while invoking the method " + funcName +
                            targetException.getMessage());
                    return targetException;
                } catch (IllegalAccessException e) {
                    throw new ProjectException("Method has private access", e);
                }
            };
            final FutureValue out = scheduler
                    .schedule(new Object[1], func, null, null, null, PredefinedTypes.TYPE_ANY, null, null);
            scheduler.start();
            if (byteArrayOutputStream.size() > 0) {
                ContentServer.getInstance().sendMessage(byteArrayOutputStream.toString());
            }
            final Throwable t = out.getPanic();
            if (t != null) {
                ContentServer.getInstance().sendMessage("Error occurred while invoking method " + funcName);
            }
        } catch (NoSuchMethodException e) {
            ContentServer.getInstance().sendMessage("Error while invoking function '" + funcName + "'" + e.getMessage());
            //return new RuntimeException("Error while invoking function '" + funcName + "'", e);
        }
    }

    private static void directRun(Class<?> initClazz, String funcName, Class[] paramTypes, Object[] args)
            throws ProjectException{
        Object response;
        try {
            // Create a stream to hold the output
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(byteArrayOutputStream);
            PrintStream existingStream = System.out;
            // Use defined stream
            System.setOut(printStream);
            final Method method = initClazz.getDeclaredMethod(funcName, paramTypes);
            response = method.invoke(null, args);
            System.out.flush();
            System.setOut(existingStream);
            if (response instanceof Throwable) {
                ContentServer.getInstance().sendMessage("Error occurred while executing method " + funcName);
            }else{
                ContentServer.getInstance().sendMessage(byteArrayOutputStream.toString());
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            ContentServer.getInstance().sendMessage("Error occurred while executing method " + funcName);
        }
    }

    private BRunUtil() {
    }
}
