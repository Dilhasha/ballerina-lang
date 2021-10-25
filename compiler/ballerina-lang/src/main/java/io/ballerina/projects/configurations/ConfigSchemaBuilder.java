package io.ballerina.projects.configurations;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;

public class ConfigSchemaBuilder {

    static ClassWriter cw = new ClassWriter(0);
    static String className = "ModuleName";

    public static void getConfigSchemaContent() {
        JacksonModule module = new JacksonModule(
                JacksonOption.RESPECT_JSONPROPERTY_REQUIRED
        );
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7,
                OptionPreset.PLAIN_JSON).with(module);
        SchemaGeneratorConfig config = configBuilder.build();
        SchemaGenerator generator = new SchemaGenerator(config);
//        //Replace packageName
////        replaceName("$PackageName", "mymock");
////        replaceName("$ModuleName", "foo");
//
////        // Create getters and setters
////        createSetter("myVal", "java.lang.String", ModuleName.class);
//        createSetter("testMode", Type.getDescriptor(boolean.class));
////        createSetter("newVal", "java.lang.Double", ModuleName.class);
//////
////        createGetter("myVal", "java.lang.String", ModuleName.class);
//        createGetter("testMode", Type.getDescriptor(boolean.class));
//        cw.visitEnd();
//        createGetter("newVal", "java.lang.Double", ModuleName.class);

        try {
            //JsonNode jsonSchema = generator.generateSchema(ConfigClass.class);
            JsonNode jsonSchema = generator.generateSchema(generateClass(null, "mymock", "foo"));
            System.out.println(jsonSchema.toString());
        } catch (Exception exception) {
            exception.printStackTrace();
        }

    }

    private static void replaceName(String textToReplace, String replaceWith) {
        Path path = Paths.get("ConfigClass.java");
        String content;
        try {
            content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            content = content.replaceAll(textToReplace, replaceWith);
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public static Class generateClass(String orgName, String packageName, String moduleName) throws Exception {

        ClassWriter cw = new ClassWriter(0);

        cw.visit(52, ACC_SUPER, "ConfigClass", null, "java/lang/Object", null);

        String moduleClass = moduleName.substring(0, 1).toUpperCase()
                + moduleName.substring(1);
        String packageClass = packageName.substring(0, 1).toUpperCase()
                + packageName.substring(1);
        //cw.visitInnerClass("ConfigClass$" + moduleClass, "ConfigClass", moduleClass, 0);
        //cw.visitInnerClass("ConfigClass$" + packageClass, "ConfigClass", packageClass, 0);
        generateConstructor(cw);

        cw.newClass(moduleClass);
        cw.newClass(packageClass);


//        generateGlobalField(cw, packageName, "LConfigClass$"+packageClass+";", false);
//        generateGetterMethod(cw, packageName, "ConfigClass", "LConfigClass$"+ packageClass+";", ARETURN);
//        generateSetterMethod(cw, packageName, "ConfigClass", "LConfigClass$"+packageClass+";", ALOAD);
//
//        generateGlobalField(cw, moduleName, "LConfigClass$"+moduleClass+";", false);
//        generateGetterMethod(cw, moduleName, "ConfigClass", "LConfigClass$"+ moduleClass+";", ARETURN);
//        generateSetterMethod(cw, moduleName, "ConfigClass", "LConfigClass$"+moduleClass+";", ALOAD);

        generateGlobalField(cw, "testMode", "Z", false);
        generateGlobalField(cw, "myVal", "Ljava/lang/String;", true);
        generateGlobalField(cw, "newVal", "D", false);

        generateGetterMethod(cw, "newVal", "ConfigClass", "D", DRETURN);
        generateGetterMethod(cw, "myVal", "ConfigClass", "Ljava/lang/String;", ARETURN);
        generateGetterMethod(cw, "testMode", "ConfigClass", "Z", IRETURN);

        generateSetterMethod(cw, "testMode", "ConfigClass", "Z", ILOAD);
        generateSetterMethod(cw, "myVal", "ConfigClass", "Ljava/lang/String;", ALOAD);
        generateSetterMethod(cw, "newVal", "ConfigClass", "D", DLOAD);
        cw.visitEnd();

        return JavaClassLoader.getGeneratedClass(cw.toByteArray(), "ConfigClass");
    }

    private static void generateGlobalField(ClassWriter cw, String propertyName, String typeDesc, boolean isRequired) {
        FieldVisitor fv;
        fv = cw.visitField(0, propertyName, typeDesc, null, null);
        if(isRequired) {
            AnnotationVisitor av = fv.visitAnnotation(
                    "Lcom/fasterxml/jackson/annotation/JsonProperty;", true);
            av.visit("required", Boolean.TRUE);
            av.visitEnd();
        }
        fv.visitEnd();
    }

    private static void generateConstructor(ClassWriter cw) {
        MethodVisitor mv;
        mv=cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(1,1);
        mv.visitEnd();
    }

    private static void generateGetterMethod(ClassWriter cw, String propertyName, String className, String typeDesc, int returnOpCode) {
        String methodName = "get" + propertyName.substring(0, 1).toUpperCase()
                + propertyName.substring(1);
        MethodVisitor mv;
        mv = cw.visitMethod(ACC_PUBLIC, methodName, "()" + typeDesc, null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, propertyName, typeDesc);
        mv.visitInsn(returnOpCode);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
    }

    private static void generateSetterMethod(ClassWriter cw, String propertyName, String className, String typeDesc, int opCode) {
        String methodName = "set" + propertyName.substring(0, 1).toUpperCase()
                + propertyName.substring(1);
        MethodVisitor mv;
        mv = cw.visitMethod(ACC_PUBLIC, methodName, "("+ typeDesc +")V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(opCode, 1);
        mv.visitFieldInsn(PUTFIELD, className, propertyName, typeDesc);
        mv.visitInsn(RETURN);
        mv.visitMaxs(3, 3);
        mv.visitEnd();
    }

    public static class JavaClassLoader extends ClassLoader {
        static Class getGeneratedClass(byte[] bytes, String className){
            JavaClassLoader javaClassLoader = new JavaClassLoader();
            return javaClassLoader.defineClass(className, bytes, 0, bytes.length);
        }

    }
}
