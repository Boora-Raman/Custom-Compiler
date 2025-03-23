import java.io.*;
import java.util.*;
import java.util.regex.*;

class Token {
    String type;
    String value;
    int line;
    int column;

    Token(String type, String value, int line, int column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString() {
        return "[" + type + ": " + value + "] at line " + line + ", column " + column;
    }
}

class ErrorHandler {
    void error(String message, int line, int column) {
        try (FileWriter errorWriter = new FileWriter("errors.txt", true)) {
            errorWriter.write("Error at line " + line + ", column " + column + ": " + message + "\n");
        } catch (IOException e) {
            System.err.println("Error writing to errors.txt: " + e.getMessage());
        }
    }
}

class SymbolTable {
    private Map<String, String> table = new HashMap<>();

    void add(String identifier, String type, int line, int column) {
        table.put(identifier, type);
        System.out.println("Added to symbol table: " + identifier + " (Type: " + type + ", Line: " + line + ", Column: " + column + ")");
    }

    boolean contains(String identifier) {
        return table.containsKey(identifier);
    }

    String getType(String identifier) {
        return table.getOrDefault(identifier, "Double");
    }
}

class ASTNode {
    String type;
    List<ASTNode> children;
    String value;

    ASTNode(String type) {
        this.type = type;
        this.children = new ArrayList<>();
    }
}

public class Compiler {
    private static final String[] KEYWORDS = {"if", "else", "for", "return", "call"};
    private static final String SINGLE_OPERATORS = "+-*/%=(){}<>;,.";
    private static final String RELATIONAL_OPERATORS = "== != <= >= < >";
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[0-9]+");
    private static final Pattern STRING_PATTERN = Pattern.compile("\"[^\"]*\"");

    private String input;
    private List<Token> tokens;
    private int pos;
    private List<String> errors;
    private SymbolTable symbolTable;
    private ErrorHandler errorHandler;

    public Compiler(String input) {
        this.input = input;
        this.tokens = new ArrayList<>();
        this.pos = 0;
        this.errors = new ArrayList<>();
        this.symbolTable = new SymbolTable();
        this.errorHandler = new ErrorHandler();
    }

    private void tokenize() {
        int line = 1;
        int column;
        String[] lines = input.split("\n");

        for (String currentLine : lines) {
            int i = 0;
            column = 1;

            while (i < currentLine.length()) {
                char ch = currentLine.charAt(i);

                if (Character.isWhitespace(ch)) {
                    if (ch == '\n') line++;
                    else column++;
                    i++;
                    continue;
                }

                if (i + 1 < currentLine.length()) {
                    String twoChars = currentLine.substring(i, i + 2);
                    if (RELATIONAL_OPERATORS.contains(twoChars)) {
                        tokens.add(new Token("OPERATOR", twoChars, line, column));
                        i += 2;
                        column += 2;
                        continue;
                    }
                }

                if (Character.isLetter(ch) || ch == '_') {
                    Matcher matcher = IDENTIFIER_PATTERN.matcher(currentLine.substring(i));
                    if (matcher.find()) {
                        String word = matcher.group();
                        String type = Arrays.asList(KEYWORDS).contains(word) ? "KEYWORD" : "IDENTIFIER";
                        tokens.add(new Token(type, word, line, column));
                        i += word.length();
                        column += word.length();
                        continue;
                    }
                }

                if (Character.isDigit(ch)) {
                    Matcher matcher = NUMBER_PATTERN.matcher(currentLine.substring(i));
                    if (matcher.find()) {
                        String number = matcher.group();
                        tokens.add(new Token("NUMBER", number, line, column));
                        i += number.length();
                        column += number.length();
                        continue;
                    }
                }

                if (ch == '"') {
                    Matcher matcher = STRING_PATTERN.matcher(currentLine.substring(i));
                    if (matcher.find()) {
                        String str = matcher.group();
                        tokens.add(new Token("STRING", str, line, column));
                        i += str.length();
                        column += str.length();
                        continue;
                    } else {
                        errors.add("Error at line " + line + ", column " + column + ": Unterminated string literal");
                        errorHandler.error("Unterminated string literal", line, column);
                        break;
                    }
                }

                if (SINGLE_OPERATORS.indexOf(ch) != -1) {
                    tokens.add(new Token("OPERATOR", String.valueOf(ch), line, column));
                    i++;
                    column++;
                    continue;
                }

                errors.add("Error at line " + line + ", column " + column + ": Unexpected character: " + ch);
                errorHandler.error("Unexpected character: " + ch, line, column);
                i++;
                column++;
            }
            line++;
        }
    }

    private ASTNode parse() {
        tokenize();
        ASTNode program = new ASTNode("Program");
        while (pos < tokens.size()) {
            ASTNode element = parseProgramElement();
            if (element != null) program.children.add(element);
            else pos++;
        }
        return program;
    }

    private ASTNode parseProgramElement() {
        Token current = currentToken();
        if (current == null) return null;

        if ("IDENTIFIER".equals(current.type)) {
            Token next = peek(1);
            if (next != null && "(".equals(next.value)) {
                int lookahead = 2;
                while (lookahead < tokens.size() && !tokens.get(pos + lookahead).value.equals(")")) {
                    lookahead++;
                }
                if (lookahead + 1 < tokens.size() && tokens.get(pos + lookahead).value.equals(")") &&
                    tokens.get(pos + lookahead + 1).value.equals("{")) {
                    return parseFunctionDefinition();
                }
            }
            return parseAssignment();  // Handle top-level assignments
        } else if ("KEYWORD".equals(current.type) && "call".equals(current.value)) {
            return parseFunctionCall();
        }
        errors.add("Error at line " + current.line + ", column " + current.column + ": Expected assignment, function definition, or 'call'");
        return null;
    }

    private ASTNode parseFunctionDefinition() {
        ASTNode func = new ASTNode("Function");
        Token name = consume("IDENTIFIER");
        func.value = name.value;
        symbolTable.add(name.value, "Function", name.line, name.column);

        consume("OPERATOR", "(");
        func.children.add(parseParameterList());
        consume("OPERATOR", ")");

        consume("OPERATOR", "{");
        
        while (currentToken() != null && !"}".equals(currentToken().value)) {
            if ("KEYWORD".equals(currentToken().type) && "return".equals(currentToken().value)) {
                ASTNode returnStmt = new ASTNode("Return");
                consume("KEYWORD", "return");
                ASTNode expr = parseExpression();
                if (expr != null) returnStmt.children.add(expr);
                consume("OPERATOR", ";");
                func.children.add(returnStmt);
            } else {
                ASTNode stmt = parseStatement();
                if (stmt != null) func.children.add(stmt);
                else break;
            }
        }

        consume("OPERATOR", "}");
        
        String returnType = "Double";
        for (ASTNode child : func.children) {
            if ("Return".equals(child.type) && !child.children.isEmpty()) {
                returnType = inferReturnType(child.children.get(0));
                break;
            }
        }
        symbolTable.add(name.value, returnType, name.line, name.column);
        
        return func;
    }

    private ASTNode parseParameterList() {
        ASTNode params = new ASTNode("Parameters");
        Token current = currentToken();
        if (current != null && !")".equals(current.value)) {
            Token param = consume("IDENTIFIER");
            if (param != null) {
                ASTNode paramNode = new ASTNode("Parameter");
                paramNode.value = param.value;
                params.children.add(paramNode);
                symbolTable.add(param.value, "Double", param.line, param.column);
            }

            while (currentToken() != null && ",".equals(currentToken().value)) {
                consume("OPERATOR", ",");
                param = consume("IDENTIFIER");
                if (param != null) {
                    ASTNode paramNode = new ASTNode("Parameter");
                    paramNode.value = param.value;
                    params.children.add(paramNode);
                    symbolTable.add(param.value, "Double", param.line, param.column);
                }
            }
        }
        return params;
    }

    private ASTNode parseStatement() {
        Token current = currentToken();
        if (current == null) return null;

        if ("KEYWORD".equals(current.type) && "call".equals(current.value)) {
            return parseFunctionCall();
        } else if ("IDENTIFIER".equals(current.type)) {
            return parseAssignment();
        }
        errors.add("Error at line " + current.line + ", column " + current.column + ": Expected 'call' or assignment");
        return null;
    }

    private ASTNode parseAssignment() {
        Token idToken = consume("IDENTIFIER");
        if (idToken == null) return null;

        Token next = currentToken();
        if (next != null && "=".equals(next.value)) {
            ASTNode stmt = new ASTNode("Assignment");
            stmt.value = idToken.value;
            consume("OPERATOR", "=");
            int exprStartColumn = currentToken() != null ? currentToken().column : next.column + 1;
            ASTNode expr = parseExpression();
            if (expr == null) {
                errors.add("Error at line " + idToken.line + ", column " + exprStartColumn + ": Expected expression after '='");
                return null;
            }
            stmt.children.add(expr);
            Token semicolon = consume("OPERATOR", ";");
            if (semicolon == null) {
                errors.add("Error at line " + idToken.line + ", column " + exprStartColumn + ": Expected ';' after assignment");
                return null;
            }
            symbolTable.add(idToken.value, inferReturnType(expr), idToken.line, idToken.column);
            return stmt;
        }
        errors.add("Error at line " + idToken.line + ", column " + idToken.column + ": Expected '=' after identifier in assignment");
        return null;
    }

    private ASTNode parseFunctionCall() {
        consume("KEYWORD", "call");
        Token idToken = consume("IDENTIFIER");
        if (idToken == null) {
            errors.add("Error at line " + (currentToken() != null ? currentToken().line : 0) + 
                ", column " + (currentToken() != null ? currentToken().column : 0) + ": Expected identifier after 'call'");
            return null;
        }
        ASTNode stmt = new ASTNode("FunctionCall");
        stmt.value = idToken.value;
        consume("OPERATOR", "(");
        stmt.children.add(parseArgumentList());
        consume("OPERATOR", ")");
        consume("OPERATOR", ";");
        if (!symbolTable.contains(idToken.value) && !"print".equals(idToken.value)) {
            errors.add("Error at line " + idToken.line + ", column " + idToken.column + ": Undefined function '" + idToken.value + "'");
        }
        return stmt;
    }

    private ASTNode parseExpression() {
        return parseComparisonExpression();
    }

    private ASTNode parseComparisonExpression() {
        ASTNode left = parseAdditiveExpression();
        Token current = currentToken();
        if (current != null && RELATIONAL_OPERATORS.contains(current.value)) {
            String op = consume("OPERATOR").value;
            ASTNode right = parseAdditiveExpression();
            if (right != null) {
                ASTNode compNode = new ASTNode("Comparison");
                compNode.value = op;
                compNode.children.add(left);
                compNode.children.add(right);
                return compNode;
            }
        }
        return left;
    }

    private ASTNode parseAdditiveExpression() {
        ASTNode left = parseMultiplicativeExpression();
        while (currentToken() != null && ("+".equals(currentToken().value) || "-".equals(currentToken().value))) {
            String op = consume("OPERATOR").value;
            ASTNode right = parseMultiplicativeExpression();
            if (right != null) {
                ASTNode newNode = new ASTNode("BinaryOp");
                newNode.value = op;
                newNode.children.add(left);
                newNode.children.add(right);
                left = newNode;
            }
        }
        return left;
    }

    private ASTNode parseMultiplicativeExpression() {
        ASTNode left = parseFactor();
        while (currentToken() != null && ("*".equals(currentToken().value) || "/".equals(currentToken().value) || "%".equals(currentToken().value))) {
            String op = consume("OPERATOR").value;
            ASTNode right = parseFactor();
            if (right != null) {
                ASTNode newNode = new ASTNode("BinaryOp");
                newNode.value = op;
                newNode.children.add(left);
                newNode.children.add(right);
                left = newNode;
            }
        }
        return left;
    }

    private ASTNode parseFactor() {
        Token current = currentToken();
        if (current == null) return null;

        if ("NUMBER".equals(current.type)) {
            ASTNode node = new ASTNode("Literal");
            node.value = consume("NUMBER").value;
            return node;
        } else if ("STRING".equals(current.type)) {
            ASTNode node = new ASTNode("Literal");
            node.value = consume("STRING").value;
            return node;
        } else if ("KEYWORD".equals(current.type) && "call".equals(current.value)) {
            consume("KEYWORD", "call");
            Token idToken = consume("IDENTIFIER");
            if (idToken == null) {
                errors.add("Error at line " + current.line + ", column " + current.column + ": Expected identifier after 'call'");
                return null;
            }
            ASTNode call = new ASTNode("FunctionCall");
            call.value = idToken.value;
            consume("OPERATOR", "(");
            call.children.add(parseArgumentList());
            consume("OPERATOR", ")");
            if (!symbolTable.contains(call.value) && !"print".equals(call.value)) {
                errors.add("Error at line " + idToken.line + ", column " + idToken.column + ": Undefined function '" + call.value + "'");
            }
            return call;
        } else if ("IDENTIFIER".equals(current.type)) {
            String id = consume("IDENTIFIER").value;
            ASTNode node = new ASTNode("Variable");
            node.value = id;
            if (!symbolTable.contains(id)) {
                errors.add("Error at line " + current.line + ", column " + current.column + ": Undefined variable '" + id + "'");
            }
            return node;
        } else if ("(".equals(current.value)) {
            consume("OPERATOR", "(");
            ASTNode expr = parseExpression();
            consume("OPERATOR", ")");
            return expr;
        }
        errors.add("Error at line " + current.line + ", column " + current.column + ": Invalid expression factor");
        return null;
    }

    private ASTNode parseArgumentList() {
        ASTNode args = new ASTNode("Arguments");
        Token current = currentToken();
        if (current != null && !")".equals(current.value)) {
            ASTNode expr = parseExpression();
            if (expr != null) args.children.add(expr);
            while (currentToken() != null && ",".equals(currentToken().value)) {
                consume("OPERATOR", ",");
                expr = parseExpression();
                if (expr != null) args.children.add(expr);
            }
        }
        return args;
    }

    private Token currentToken() {
        return pos < tokens.size() ? tokens.get(pos) : null;
    }

    private Token peek(int ahead) {
        return pos + ahead < tokens.size() ? tokens.get(pos + ahead) : null;
    }

    private Token consume(String expectedType) {
        Token current = currentToken();
        if (current != null && expectedType.equals(current.type)) {
            pos++;
            return current;
        }
        if (current != null) {
            errors.add("Error at line " + current.line + ", column " + current.column +
                ": Expected " + expectedType + " but found " + current.type + "('" + current.value + "')");
        }
        return null;
    }

    private Token consume(String expectedType, String expectedValue) {
        Token current = currentToken();
        if (current != null && expectedType.equals(current.type) && expectedValue.equals(current.value)) {
            pos++;
            return current;
        }
        if (current != null) {
            errors.add("Error at line " + current.line + ", column " + current.column +
                ": Expected " + expectedValue + " but found " + current.value);
        }
        return null;
    }

    private String inferReturnType(ASTNode expr) {
        if (expr == null) return "Double";
        if ("Literal".equals(expr.type)) return expr.value.startsWith("\"") ? "String" : "Double";
        if ("BinaryOp".equals(expr.type)) {
            String leftType = inferReturnType(expr.children.get(0));
            String rightType = inferReturnType(expr.children.get(1));
            return "String".equals(leftType) || "String".equals(rightType) ? "String" : "Double";
        }
        if ("Comparison".equals(expr.type)) return "Boolean";
        if ("Variable".equals(expr.type)) return symbolTable.getType(expr.value);
        if ("FunctionCall".equals(expr.type)) return symbolTable.getType(expr.value);
        return "Double";
    }

    public String generateCode(ASTNode ast) {
        StringBuilder output = new StringBuilder();
        output.append("public class CompilerOutput {\n");

        // Generate function definitions
        Map<String, ASTNode> functions = new HashMap<>();
        for (ASTNode element : ast.children) {
            if ("Function".equals(element.type)) {
                functions.put(element.value, element);
            }
        }
        for (ASTNode func : functions.values()) {
            String returnType = symbolTable.getType(func.value);
            output.append("    public static ").append(returnType).append(" ")
                .append(func.value).append("(");

            ASTNode params = func.children.get(0);
            for (int i = 0; i < params.children.size(); i++) {
                if (i > 0) output.append(", ");
                output.append("Double ").append(params.children.get(i).value);
            }
            output.append(") {\n");

            for (int i = 1; i < func.children.size(); i++) {
                if ("Return".equals(func.children.get(i).type)) {
                    output.append("        return ").append(generateExpression(func.children.get(i).children.get(0))).append(";\n");
                } else {
                    output.append("        ").append(generateStatement(func.children.get(i)));
                }
            }
            output.append("    }\n\n");
        }

        // Generate main method
        output.append("    public static void main(String[] args) {\n");
        
        // Process all statements in order
        for (ASTNode element : ast.children) {
            if ("Assignment".equals(element.type)) {
                String type = inferReturnType(element.children.get(0));
                output.append("        ").append(type).append(" ").append(element.value)
                    .append(" = ").append(generateExpression(element.children.get(0))).append(";\n");
            } else if ("FunctionCall".equals(element.type)) {
                if ("print".equals(element.value)) {
                    if (element.children.get(0).children.isEmpty()) {
                        output.append("        System.out.println();\n");
                    } else {
                        output.append("        System.out.println(")
                            .append(generateExpression(element.children.get(0).children.get(0))).append(");\n");
                    }
                } else {
                    output.append("        System.out.println(")
                        .append(generateExpression(element)).append(");\n");
                }
            }
        }
        
        output.append("    }\n");
        output.append("}\n");

        if (!errors.isEmpty()) {
            output.append("\n/* Compilation Errors:\n");
            for (String error : errors) {
                output.append(error).append("\n");
            }
            output.append("*/");
        }

        return output.toString();
    }

    private String generateStatement(ASTNode stmt) {
        if ("FunctionCall".equals(stmt.type)) {
            return generateExpression(stmt) + ";\n";
        } else if ("Assignment".equals(stmt.type)) {
            String type = inferReturnType(stmt.children.get(0));
            return type + " " + stmt.value + " = " + generateExpression(stmt.children.get(0)) + ";\n";
        }
        return "";
    }

    private String generateExpression(ASTNode expr) {
        if ("Literal".equals(expr.type)) return expr.value;
        if ("Variable".equals(expr.type)) return expr.value;
        if ("BinaryOp".equals(expr.type)) {
            return generateExpression(expr.children.get(0)) + " " + expr.value + " " +
                generateExpression(expr.children.get(1));
        }
        if ("Comparison".equals(expr.type)) {
            return generateExpression(expr.children.get(0)) + " " + expr.value + " " +
                generateExpression(expr.children.get(1));
        }
        if ("FunctionCall".equals(expr.type)) {
            return expr.value + "(" + generateArguments(expr.children.get(0)) + ")";
        }
        return "";
    }

    private String generateArguments(ASTNode args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.children.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(generateExpression(args.children.get(i)));
        }
        return sb.toString();
    }

    public String compile() {
        ASTNode ast = parse();
        return generateCode(ast);
    }

    private static String readFile(String filename) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private static void writeFile(String filename, String content) throws IOException {
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(content);
        }
    }

    private static void writeTokens(String filename, List<Token> tokens) throws IOException {
        try (FileWriter writer = new FileWriter(filename)) {
            for (Token token : tokens) {
                writer.write(token.toString() + "\n");
            }
        }
    }

    public static void main(String[] args) {
        try {
            String inputFile = "input.txt";
            String outputFile = "CompilerOutput.java";
            if (args.length > 0) inputFile = args[0];
            if (args.length > 1) outputFile = args[1];

            String code = readFile(inputFile);
            Compiler compiler = new Compiler(code);
            String javaCode = compiler.compile();

            writeFile(outputFile, javaCode);
            writeTokens("tokens.txt", compiler.tokens);
            System.out.println("Compilation complete. Output written to " + outputFile + " and tokens.txt");
            System.out.println(javaCode);

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
