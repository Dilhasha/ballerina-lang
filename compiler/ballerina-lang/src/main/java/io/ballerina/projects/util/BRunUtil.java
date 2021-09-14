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
@SuppressWarnings("ALL")
public class BRunUtil {

    private static final String MODULE_INIT_CLASS_NAME = "$_init";
    private static final String MAIN_CLASS_NAME = "main";
    private static final String CONFIGURATION_CLASS_NAME = "$configurationMapper";

    public static Class<?> mainClazz;
    public static Class<?> configClazz;
    public static Class<?> initClazz;
    public static boolean classLoaded;


    public static void runGeneratedExecutable(final Project project, final List<String> args, final PrintStream err) {
        final Module executableModule = project.currentPackage().getDefaultModule();
        long start = System.currentTimeMillis();
        final PackageCompilation packageCompilation = project.currentPackage().getCompilation();
        System.out.println("packageCompilationDuration: " + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();
        final JBallerinaBackend jBallerinaBackend = JBallerinaBackend.from(packageCompilation, JvmTarget.JAVA_11);
        System.out.println("codeGenDuration: " + (System.currentTimeMillis() - start));
        final JarResolver jarResolver = jBallerinaBackend.jarResolver();

        start = System.currentTimeMillis();
        final String initClassName = JarResolver.getQualifiedClassName(
                executableModule.packageInstance().packageOrg().toString(),
                executableModule.packageInstance().packageName().toString(),
                executableModule.packageInstance().packageVersion().toString(),
                BRunUtil.MODULE_INIT_CLASS_NAME);
        try {
            final List<String> commands = new ArrayList<>();
            commands.add(System.getProperty("java.command"));
            commands.add("-XX:+HeapDumpOnOutOfMemoryError");
            commands.add("-XX:HeapDumpPath=" + System.getProperty(USER_DIR));
            // Sets classpath with executable thin jar and all dependency jar paths.
            commands.add("-cp");
            commands.add(BRunUtil.getAllClassPaths(jarResolver));
            commands.add(initClassName);
            commands.addAll(args);
            final ProcessBuilder pb = new ProcessBuilder(commands).inheritIO();
            final Process process = pb.start();
            process.waitFor();
            final int exitValue = process.exitValue();
            if (exitValue != 0) {
                throw new ProjectException("Error occurred");
            }
        } catch (final IOException | InterruptedException e) {
            throw new ProjectException("Error occurred while running the executable ", e.getCause());
        }
        System.out.println("api process execution time: " + (System.currentTimeMillis() - start));
    }

    private static String getAllClassPaths(final JarResolver jarResolver) {
        final StringJoiner cp = new StringJoiner(File.pathSeparator);
        jarResolver.getJarFilePathsRequiredForExecution().stream()
                .map(JarLibrary::path).map(Path::toString)
                .forEach(cp::add);
        return cp.toString();
    }

    public static void run(final Project project, final boolean isChanged) {
        final Package currentPackage = project.currentPackage();
        ClassLoader classLoader = null;
        if (!isChanged || !BRunUtil.classLoaded) {
            // Get resolution
            long start = System.currentTimeMillis();
            currentPackage.getResolution();
            System.out.println("package resolution duration: " + (System.currentTimeMillis() - start));
            start = System.currentTimeMillis();
            // Compile
            PackageCompilation packageCompilation = currentPackage.getCompilation();
            System.out.println("package compilation duration: " + (System.currentTimeMillis() - start));
            if (packageCompilation.diagnosticResult().hasErrors()) {
                ContentServer.getInstance().sendMessage("Compilation failed with errors: " +
                        currentPackage.project().sourceRoot());
                return;
            }
            // Codegen
            start = System.currentTimeMillis();
            final JBallerinaBackend jBallerinaBackend = JBallerinaBackend.from(packageCompilation, JvmTarget.JAVA_11);
            System.out.println("codeGen duration: " + (System.currentTimeMillis() - start));
            if (jBallerinaBackend.diagnosticResult().hasErrors()) {
                ContentServer.getInstance().sendMessage("Code generation failed with errors: " +
                        currentPackage.project().sourceRoot());
                return;
            }
            BRunUtil.updateClassLoaders(
                    jBallerinaBackend.jarResolver().getClassLoaderWithRequiredJarFilesForExecution(),
                    currentPackage.manifest());
            BRunUtil.executeMain();

        } else {
            // Skip class loading if there are no changes and classes are already loaded
            BRunUtil.executeMain();
        }
    }

    private static void updateClassLoaders(final ClassLoader classLoader, final PackageManifest packageManifest) {
        final String org = packageManifest.org().toString();
        final String module = packageManifest.name().toString();
        final String version = packageManifest.version().toString();
        final String initClassName = JarResolver.getQualifiedClassName(org, module, version, BRunUtil.MODULE_INIT_CLASS_NAME);
        final String mainClassName = JarResolver.getQualifiedClassName(org, module, version, BRunUtil.MAIN_CLASS_NAME);
        final String configClassName = JarResolver.getQualifiedClassName(org, module, version, BRunUtil.CONFIGURATION_CLASS_NAME);
        try {
            BRunUtil.initClazz = classLoader.loadClass(initClassName);
            BRunUtil.mainClazz = classLoader.loadClass(mainClassName);
            BRunUtil.configClazz = classLoader.loadClass(configClassName);
        } catch (final ClassNotFoundException e) {
            ContentServer.getInstance().sendMessage("Error while loading classes for execution. " +  e.getMessage());
            return;
        }
        BRunUtil.classLoaded = true;
    }

    private static void executeMain(){
        final long start = System.currentTimeMillis();
        Scheduler scheduler = new Scheduler(false);
        final TomlDetails configurationDetails = LaunchUtils.getConfigurationDetails();
        BRunUtil.directRun(BRunUtil.configClazz, "$configureInit",
                new Class[]{String[].class, Path[].class, String.class}, new Object[]{new String[]{},
                        configurationDetails.paths, configurationDetails.configContent});
        LaunchUtils.startListeners(false);
        try {
            BRunUtil.runOnSchedule(BRunUtil.initClazz, ASTBuilderUtil.createIdentifier(null, "$moduleInit"),
                    scheduler);
            BRunUtil.runOnSchedule(BRunUtil.mainClazz, ASTBuilderUtil.createIdentifier(null, "main"),
                    scheduler);
            BRunUtil.runOnSchedule(BRunUtil.initClazz, ASTBuilderUtil.createIdentifier(null, "$moduleStart"),
                    scheduler);
            System.out.println("Program execution duration: " + (System.currentTimeMillis() - start));
        } catch (final Exception exception) {
            ContentServer.getInstance().sendMessage("Execption occurred while running the program " + exception.getMessage());
        }
    }

    private static void runOnSchedule(final Class<?> initClazz, final BLangIdentifier name, final Scheduler scheduler) {
        final String funcName = JvmCodeGenUtil.cleanupFunctionName(name.value);
        try {
            Method method = initClazz.getDeclaredMethod(funcName, Strand.class);
            // Create a stream to hold the output
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final PrintStream printStream = new PrintStream(byteArrayOutputStream);
            final PrintStream existingStream = System.out;
            // Use defined stream
            System.setOut(printStream);
            //TODO fix following method invoke to scheduler.schedule()
            final Function<Object[], Object> func = objects -> {
                try {
                    final Object response = method.invoke(null, objects[0]);
                    System.out.flush();
                    System.setOut(existingStream);
                    return response;
                } catch (final InvocationTargetException e) {
                    final Throwable targetException = e.getTargetException();
                    ContentServer.getInstance().sendMessage("Error occurred while invoking the method " + funcName +
                            targetException.getMessage());
                    return targetException;
                } catch (final IllegalAccessException e) {
                    throw new ProjectException("Method has private access", e);
                }
            };
            FutureValue out = scheduler
                    .schedule(new Object[1], func, null, null, null, PredefinedTypes.TYPE_ANY, null, null);
            scheduler.start();
            if (byteArrayOutputStream.size() > 0) {
                ContentServer.getInstance().sendMessage(byteArrayOutputStream.toString());
            }
            Throwable t = out.getPanic();
            if (t != null) {
                ContentServer.getInstance().sendMessage("Error occurred while invoking method " + funcName);
            }
        } catch (final NoSuchMethodException e) {
            ContentServer.getInstance().sendMessage("Error while invoking function '" + funcName + "'" + e.getMessage());
            //return new RuntimeException("Error while invoking function '" + funcName + "'", e);
        }
    }

    private static void directRun(final Class<?> initClazz, final String funcName, final Class[] paramTypes, final Object[] args)
            throws ProjectException{
        final Object response;
        try {
            // Create a stream to hold the output
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final PrintStream printStream = new PrintStream(byteArrayOutputStream);
            final PrintStream existingStream = System.out;
            // Use defined stream
            System.setOut(printStream);
            Method method = initClazz.getDeclaredMethod(funcName, paramTypes);
            response = method.invoke(null, args);
            System.out.flush();
            System.setOut(existingStream);
            if (response instanceof Throwable) {
                ContentServer.getInstance().sendMessage("Error occurred while executing method " + funcName);
            }else{
                ContentServer.getInstance().sendMessage(byteArrayOutputStream.toString());
            }
        } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            ContentServer.getInstance().sendMessage("Error occurred while executing method " + funcName);
        }
    }

    private BRunUtil() {
    }
}
