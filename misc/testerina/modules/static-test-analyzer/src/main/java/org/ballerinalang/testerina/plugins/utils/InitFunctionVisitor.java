package org.ballerinalang.testerina.plugins.utils;

import io.ballerina.compiler.syntax.tree.AnnotationNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.MetadataNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.NodeVisitor;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Init function visitor.
 *
 *  @since 2.0.0
 */
public class InitFunctionVisitor extends NodeVisitor {

    private static final String INIT_ANNOTATION_NAME = "Init";
    private static final String TEST_MODULE_NAME = "test";

    private final List<FunctionDefinitionNode> functions;

    public InitFunctionVisitor() {
        this.functions = new ArrayList<>();
    }

    @Override
    public void visit(ModulePartNode modulePartNode) {
        super.visit(modulePartNode);
    }

    @Override
    public void visit(FunctionDefinitionNode functionDefinitionNode) {
        if (functionDefinitionNode.metadata().isEmpty()) {
            return;
        }
        MetadataNode metadataNode = functionDefinitionNode.metadata().get();
        for (AnnotationNode annotation : metadataNode.annotations()) {
            if (annotation.annotReference().kind() != SyntaxKind.QUALIFIED_NAME_REFERENCE) {
                continue;
            }
            QualifiedNameReferenceNode qualifiedNameReferenceNode =
                    (QualifiedNameReferenceNode) annotation.annotReference();
            String modulePrefix = qualifiedNameReferenceNode.modulePrefix().text();
            String identifier = qualifiedNameReferenceNode.identifier().text();
            if (TEST_MODULE_NAME.equals(modulePrefix) && INIT_ANNOTATION_NAME.equals(identifier)) {
                functions.add(functionDefinitionNode);
            }
        }
    }

    public List<FunctionDefinitionNode> getFunctions() {
        return this.functions;
    }
}
