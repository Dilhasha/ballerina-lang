package org.ballerinalang.testerina.plugins.utils;

import io.ballerina.compiler.syntax.tree.CaptureBindingPatternNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.ExpressionStatementNode;
import io.ballerina.compiler.syntax.tree.FunctionArgumentNode;
import io.ballerina.compiler.syntax.tree.FunctionBodyBlockNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.FunctionSignatureNode;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.MethodCallExpressionNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeFactory;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.OptionalTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.PositionalArgumentNode;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.ReturnTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.SimpleNameReferenceNode;
import io.ballerina.compiler.syntax.tree.StatementNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.compiler.syntax.tree.TypedBindingPatternNode;
import io.ballerina.compiler.syntax.tree.VariableDeclarationNode;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentConfig;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleId;
import io.ballerina.projects.Package;
import io.ballerina.projects.Project;
import io.ballerina.projects.plugins.AnalysisTask;
import io.ballerina.projects.plugins.CompilationAnalysisContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Analysis task used to register already defined test cases.
 *
 * @since 2.0.0
 */
public class TestRegistrationTask implements AnalysisTask<CompilationAnalysisContext> {

    public static final String TEST_EXEC_BAL = "test_executer.bal";

    @Override
    public void perform(CompilationAnalysisContext compilationAnalysisContext) {
        Package currentPackage = compilationAnalysisContext.currentPackage();
        List<FunctionDefinitionNode> functions = getTestFunctions(currentPackage);
        Module module = currentPackage.getDefaultModule();
        DocumentConfig documentConfig = generateDocument(currentPackage.project(), module, functions);
        if (!isDocumentExistInModule(module, documentConfig)) {
            module.modify().addDocument(documentConfig).apply();
            currentPackage.getCompilation();
        }

    }

    public List<FunctionDefinitionNode> getTestFunctions(Package currentPackage) {
        List<FunctionDefinitionNode> testFunctions = new ArrayList<>();
        for (ModuleId moduleId : currentPackage.moduleIds()) {
            Module module = currentPackage.module(moduleId);
            for (DocumentId documentId : module.documentIds()) {
                Document document = module.document(documentId);
                Node node = document.syntaxTree().rootNode();
                TestFunctionVisitor testFunctionVisitor = new TestFunctionVisitor();
                node.accept(testFunctionVisitor);
                testFunctions.addAll(testFunctionVisitor.getFunctions());
            }
        }
        return testFunctions;
    }

    private DocumentConfig generateDocument(Project project, Module module, List<FunctionDefinitionNode> functions) {
        //Create modulePartNode
        ModulePartNode modulePartNode = createModulePartNode(functions);
        String newFileContent = modulePartNode.toSourceCode();
        Path filePath = project.sourceRoot().resolve(TEST_EXEC_BAL);
        DocumentId newDocumentId = DocumentId.create(filePath.toString(), module.moduleId());
        return DocumentConfig.from(newDocumentId, newFileContent, TEST_EXEC_BAL);
    }

    public static boolean isDocumentExistInModule(Module module, DocumentConfig document) {
        for (DocumentId documentId : module.documentIds()) {
            Document doc = module.document(documentId);
            if (document.name().equals(doc.name())) {
                return true;
            }
        }
        return false;
    }

    private static ExpressionStatementNode invokeRegisterFunction(String testNameVal, String testFuncVal) {
        SimpleNameReferenceNode registerReferenceNode =
                NodeFactory.createSimpleNameReferenceNode(NodeFactory.createIdentifierToken("register"));
        SimpleNameReferenceNode registrarRef =
                NodeFactory.createSimpleNameReferenceNode(NodeFactory.createIdentifierToken("registrar"));
        PositionalArgumentNode testName =
                NodeFactory.createPositionalArgumentNode(NodeFactory
                        .createBasicLiteralNode(SyntaxKind.STRING_LITERAL, NodeFactory
                                .createLiteralValueToken(SyntaxKind.STRING_LITERAL_TOKEN,
                                        "\"" + testNameVal + "\"",
                                        NodeFactory.createEmptyMinutiaeList(),
                                        NodeFactory.createEmptyMinutiaeList())));
        PositionalArgumentNode testFunction = NodeFactory.createPositionalArgumentNode(
                NodeFactory.createSimpleNameReferenceNode(NodeFactory.createIdentifierToken(testFuncVal)));

        SeparatedNodeList<FunctionArgumentNode> separatedNodeList = getFunctionParamList(testName, testFunction);
        MethodCallExpressionNode registerCall = NodeFactory.createMethodCallExpressionNode(registrarRef,
                NodeFactory.createToken(SyntaxKind.DOT_TOKEN),
                registerReferenceNode, NodeFactory.createToken(SyntaxKind.OPEN_PAREN_TOKEN), separatedNodeList,
                NodeFactory.createToken(SyntaxKind.CLOSE_PAREN_TOKEN));

        ExpressionStatementNode expressionStatementNode =
                NodeFactory.createExpressionStatementNode(SyntaxKind.CALL_STATEMENT, registerCall,
                        NodeFactory.createToken(SyntaxKind.SEMICOLON_TOKEN));
        return expressionStatementNode;
    }

    public static FunctionDefinitionNode createInitFunction(List<FunctionDefinitionNode> functions) {
        OptionalTypeDescriptorNode optionalErrorTypeDescriptorNode =
                NodeFactory.createOptionalTypeDescriptorNode(
                        NodeFactory.createParameterizedTypeDescriptorNode(
                                SyntaxKind.ERROR_TYPE_DESC, NodeFactory.createToken(SyntaxKind.ERROR_KEYWORD), null),
                        NodeFactory.createToken(SyntaxKind.QUESTION_MARK_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))));

        ReturnTypeDescriptorNode returnTypeDescriptorNode =
                NodeFactory.createReturnTypeDescriptorNode(NodeFactory
                                .createToken(SyntaxKind.RETURNS_KEYWORD, NodeFactory.createMinutiaeList(
                                        NodeFactory.createWhitespaceMinutiae(" ")),
                                        NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))),
                        NodeFactory.createEmptyNodeList(), optionalErrorTypeDescriptorNode);
        FunctionSignatureNode functionSignatureNode =
                NodeFactory.createFunctionSignatureNode(NodeFactory.createToken(SyntaxKind.OPEN_PAREN_TOKEN),
                        NodeFactory.createSeparatedNodeList(),
                        NodeFactory.createToken(SyntaxKind.CLOSE_PAREN_TOKEN), returnTypeDescriptorNode);

        //Function body

        //start - registrar declaration
        QualifiedNameReferenceNode registrarType =
                NodeFactory.createQualifiedNameReferenceNode(NodeFactory.createIdentifierToken("dtest"),
                        NodeFactory.createToken(SyntaxKind.COLON_TOKEN),
                        NodeFactory.createIdentifierToken("Registrar", NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))));

        CaptureBindingPatternNode registrar =
                NodeFactory.createCaptureBindingPatternNode(
                        NodeFactory.createIdentifierToken("registrar", NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))));
        TypedBindingPatternNode registrarTypeBindingNode =
                NodeFactory.createTypedBindingPatternNode(registrarType, registrar);
        ExpressionNode createRegistrarNode = NodeFactory.createCheckExpressionNode(SyntaxKind.CHECK_EXPRESSION,
                NodeFactory.createToken(SyntaxKind.CHECK_KEYWORD, NodeFactory.createEmptyMinutiaeList(),
                        NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))),
                createFunctionInvocationNode("getRegistrar", new PositionalArgumentNode[0]));

        VariableDeclarationNode registrarInitialization = NodeFactory
                .createVariableDeclarationNode(NodeFactory.createEmptyNodeList(), null, registrarTypeBindingNode,
                        NodeFactory.createToken(SyntaxKind.EQUAL_TOKEN), createRegistrarNode,
                        NodeFactory.createToken(SyntaxKind.SEMICOLON_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae("\n"))));
        //end - registrar declaration

        List<StatementNode> statements = new ArrayList<>();
        statements.add(registrarInitialization);
        for (FunctionDefinitionNode func : functions) {
            statements.add(invokeRegisterFunction(func.functionName().toString(), func.functionName().toString()));
        }
        FunctionBodyBlockNode functionBodyNode =
                NodeFactory.createFunctionBodyBlockNode(
                        NodeFactory.createToken(SyntaxKind.OPEN_BRACE_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae("\n"))),
                        null,
                        NodeFactory.createNodeList(statements.toArray(new StatementNode[0])),
                        NodeFactory.createToken(SyntaxKind.CLOSE_BRACE_TOKEN));
        return NodeFactory.createFunctionDefinitionNode(
                SyntaxKind.FUNCTION_DEFINITION, null,
                NodeFactory.createEmptyNodeList(),
                NodeFactory.createToken(SyntaxKind.FUNCTION_KEYWORD, NodeFactory.createEmptyMinutiaeList(),
                        NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))),
                NodeFactory.createIdentifierToken("init",
                        NodeFactory.createEmptyMinutiaeList(), NodeFactory.createMinutiaeList()),
                NodeFactory.createEmptyNodeList(), functionSignatureNode, functionBodyNode);
    }

    private static SeparatedNodeList<FunctionArgumentNode> getFunctionParamList(PositionalArgumentNode... args) {
        List<Node> nodeList = new ArrayList<>();
        for (PositionalArgumentNode arg : args) {
            nodeList.add(arg);
            nodeList.add(NodeFactory.createToken(SyntaxKind.COMMA_TOKEN));
        }
        if (args.length > 0) {
            nodeList.remove(nodeList.size() - 1);
        }
        return NodeFactory.createSeparatedNodeList(nodeList);
    }

    public static ExpressionNode createFunctionInvocationNode(String functionName, PositionalArgumentNode... args) {
        QualifiedNameReferenceNode simpleNameReferenceNode =
                NodeFactory.createQualifiedNameReferenceNode(NodeFactory.createIdentifierToken("dtest"),
                        NodeFactory.createToken(SyntaxKind.COLON_TOKEN),
                        NodeFactory.createIdentifierToken(functionName));
        SeparatedNodeList<FunctionArgumentNode> separatedNodeList = getFunctionParamList(args);
        return NodeFactory.createFunctionCallExpressionNode(simpleNameReferenceNode,
                NodeFactory.createToken(SyntaxKind.OPEN_PAREN_TOKEN), separatedNodeList,
                NodeFactory.createToken(SyntaxKind.CLOSE_PAREN_TOKEN));
    }

    private static ModulePartNode createModulePartNode(List<FunctionDefinitionNode> functions) {
        ImportDeclarationNode dtestImport =
                NodeFactory.createImportDeclarationNode(NodeFactory.createToken(SyntaxKind.IMPORT_KEYWORD,
                        NodeFactory.createEmptyMinutiaeList(),
                        NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))),
                        NodeFactory.createImportOrgNameNode(
                                NodeFactory.createIdentifierToken("ballerina_test_engine"),
                                NodeFactory.createToken(SyntaxKind.DOT_TOKEN)),
                        NodeFactory.createSeparatedNodeList(
                                NodeFactory.createIdentifierToken("dtest")), null,
                        NodeFactory.createToken(SyntaxKind.SEMICOLON_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae("\n"))));
        List<ModuleMemberDeclarationNode> memberDeclarationNodeList = new ArrayList<>();
        memberDeclarationNodeList.add(createInitFunction(functions));
        NodeList<ModuleMemberDeclarationNode> nodeList = NodeFactory.createNodeList(memberDeclarationNodeList);
        Token eofToken = NodeFactory.createToken(SyntaxKind.EOF_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae("\n")));
        return NodeFactory.createModulePartNode(NodeFactory.createNodeList(dtestImport), nodeList, eofToken);
    }


}

