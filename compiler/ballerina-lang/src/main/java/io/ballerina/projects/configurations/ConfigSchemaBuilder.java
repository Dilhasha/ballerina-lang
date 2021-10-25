package io.ballerina.projects.configurations;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.ILOAD;
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
            JsonNode jsonSchema = generator.generateSchema(JavaClassLoader.dump());
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

    static void createSetter(String propertyName, String type) {
        String methodName = "set" + propertyName.substring(0, 1).toUpperCase()
                + propertyName.substring(1);
        MethodVisitor mv =
                cw.visitMethod(ACC_PUBLIC, methodName, "(" + type + ")V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ILOAD, 1);
        mv.visitFieldInsn(PUTFIELD, className, propertyName, type);
        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();
    }

    static void createGetter(String propertyName, String returnType) {
        String methodName = "get" + propertyName.substring(0, 1).toUpperCase()
                + propertyName.substring(1);
        MethodVisitor mv =
                cw.visitMethod(ACC_PUBLIC, methodName, "()" + returnType, null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, propertyName, returnType);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

//    public static byte[] dump() throws Exception {
//
//        ClassWriter cw = new ClassWriter(0);
//        FieldVisitor fv;
//        MethodVisitor mv;
//        AnnotationVisitor av0;
//
//        cw.visit(52, ACC_SUPER, "ModuleName", null, "java/lang/Object", null);
//
//        {
//            fv = cw.visitField(0, "testMode", "Z", null, null);
//            fv.visitEnd();
//        }
//        {
//            fv = cw.visitField(0, "myVal", "Ljava/lang/String;", null, null);
//            fv.visitEnd();
//        }
//        {
//            fv = cw.visitField(0, "newVal", "D", null, null);
//            fv.visitEnd();
//        }
//        {
//            mv = cw.visitMethod(0, "", "()V", null, null);
//            mv.visitCode();
//            mv.visitVarInsn(ALOAD, 0);
//            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "", "()V", false);
//            mv.visitVarInsn(ALOAD, 0);
//            mv.visitInsn(ICONST_0);
//            mv.visitFieldInsn(PUTFIELD, "ModuleName", "testMode", "Z");
//            mv.visitVarInsn(ALOAD, 0);
//            mv.visitLdcInsn("hello");
//            mv.visitFieldInsn(PUTFIELD, "ModuleName", "myVal", "Ljava/lang/String;");
//            mv.visitVarInsn(ALOAD, 0);
//            mv.visitLdcInsn(new Double("4.5"));
//            mv.visitFieldInsn(PUTFIELD, "ModuleName", "newVal", "D");
//            mv.visitInsn(RETURN);
//            mv.visitMaxs(3, 1);
//            mv.visitEnd();
//        }
//        {
//            mv = cw.visitMethod(ACC_PUBLIC, "isTestMode", "()Z", null, null);
//            mv.visitCode();
//            mv.visitVarInsn(ALOAD, 0);
//            mv.visitFieldInsn(GETFIELD, "ModuleName", "testMode", "Z");
//            mv.visitInsn(IRETURN);
//            mv.visitMaxs(1, 1);
//            mv.visitEnd();
//        }
//        {
//            mv = cw.visitMethod(ACC_PUBLIC, "setTestMode", "(Z)V", null, null);
//            mv.visitCode();
//            mv.visitVarInsn(ALOAD, 0);
//            mv.visitVarInsn(ILOAD, 1);
//            mv.visitFieldInsn(PUTFIELD, "ModuleName", "testMode", "Z");
//            mv.visitInsn(RETURN);
//            mv.visitMaxs(2, 2);
//            mv.visitEnd();
//        }
//        {
//            mv = cw.visitMethod(ACC_PUBLIC, "getMyVal", "()Ljava/lang/String;", null, null);
//            mv.visitCode();
//            mv.visitVarInsn(ALOAD, 0);
//            mv.visitFieldInsn(GETFIELD, "ModuleName", "myVal", "Ljava/lang/String;");
//            mv.visitInsn(ARETURN);
//            mv.visitMaxs(1, 1);
//            mv.visitEnd();
//        }
//        {
//            mv = cw.visitMethod(ACC_PUBLIC, "setMyVal", "(Ljava/lang/String;)V", null, null);
//            mv.visitCode();
//            mv.visitVarInsn(ALOAD, 0);
//            mv.visitVarInsn(ALOAD, 1);
//            mv.visitFieldInsn(PUTFIELD, "ModuleName", "myVal", "Ljava/lang/String;");
//            mv.visitInsn(RETURN);
//            mv.visitMaxs(2, 2);
//            mv.visitEnd();
//        }
//        {
//            mv = cw.visitMethod(ACC_PUBLIC, "getNewVal", "()D", null, null);
//            mv.visitCode();
//            mv.visitVarInsn(ALOAD, 0);
//            mv.visitFieldInsn(GETFIELD, "ModuleName", "newVal", "D");
//            mv.visitInsn(DRETURN);
//            mv.visitMaxs(2, 1);
//            mv.visitEnd();
//        }
//        {
//            mv = cw.visitMethod(ACC_PUBLIC, "setNewVal", "(D)V", null, null);
//            mv.visitCode();
//            mv.visitVarInsn(ALOAD, 0);
//            mv.visitVarInsn(DLOAD, 1);
//            mv.visitFieldInsn(PUTFIELD, "ModuleName", "newVal", "D");
//            mv.visitInsn(RETURN);
//            mv.visitMaxs(3, 3);
//            mv.visitEnd();
//        }
//        cw.visitEnd();
//
//        String filename = "ModuleName.class";
//        System.out.println("Writing " + filename);
//        FileOutputStream os = new FileOutputStream(filename);
//        os.write(cw.toByteArray());
//        os.close();
//        return cw.toByteArray();
//    }
}
