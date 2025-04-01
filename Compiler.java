import java.io.*;
import java.util.*;
import java.util.regex.*;

public class Compiler {
    private String input;

    public Compiler(String input) {
        this.input = input;
    }

    static class ASTNode {
        String type;
        List<ASTNode> children;
        String value;
        String varType;

        ASTNode(String type) {
            this.type = type;
            this.children = new ArrayList<>();
            this.value = "";
        }

        ASTNode(String type, String value) {
            this.type = type;
            this.children = new ArrayList<>();
            this.value = value;
        }
    }

    static class Token {
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

    static class SymbolTable {
        private Map<String, String> table = new HashMap<>();
        private Map<String, List<String>> functionParams = new HashMap<>();
        private Map<String, Integer> lineNumbers = new HashMap<>();
        private Map<String, Integer> columnNumbers = new HashMap<>();

        void add(String identifier, String type, int line, int column) {
            table.put(identifier, type);
            lineNumbers.put(identifier, line);
            columnNumbers.put(identifier, column);
            System.out.println("Added to symbol table: " + identifier + " (Type: " + type + ", Line: " + line + ", Column: " + column + ")");
        }

        void addFunctionParams(String funcName, List<String> paramTypes) {
            functionParams.put(funcName, paramTypes);
        }

        List<String> getFunctionParams(String funcName) {
            return functionParams.getOrDefault(funcName, Collections.emptyList());
        }

        boolean contains(String identifier) {
            return table.containsKey(identifier);
        }

        String getType(String identifier) {
            return table.getOrDefault(identifier, "Double");
        }

        int getLine(String identifier) {
            return lineNumbers.getOrDefault(identifier, 0);
        }

        int getColumn(String identifier) {
            return columnNumbers.getOrDefault(identifier, 0);
        }
    }

    static class ErrorHandler {
        void error(String message, int line, int column) {
            try (FileWriter errorWriter = new FileWriter("errors.txt", true)) {
                errorWriter.write("Error at line " + line + ", column " + column + ": " + message + "\n");
            } catch (IOException e) {
                System.err.println("Error writing to errors.txt: " + e.getMessage());
            }
        }
    }

    static class Lexer {
        private String input;
        private List<Token> tokens;
        private List<String> errors;
        private ErrorHandler errorHandler;
        private static final String[] KEYWORDS = {"if", "else", "for", "return", "call", "Double", "String"};
        private static final String SINGLE_OPERATORS = "+-*/%=(){}<>;,.[].";
        private static final String RELATIONAL_OPERATORS = "== != <= >= < >";
        private static final String LOGICAL_OPERATORS = "&& ||";
        private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
        private static final Pattern NUMBER_PATTERN = Pattern.compile("[0-9]+(\\.[0-9]+)?");
        private static final Pattern STRING_PATTERN = Pattern.compile("\"[^\"]*\"");

        public Lexer(String input) {
            this.input = input;
            this.tokens = new ArrayList<>();
            this.errors = new ArrayList<>();
            this.errorHandler = new ErrorHandler();
        }

        public List<Token> tokenize() {
            int line = 1;
            int column;
            String[] lines = input.split("\n");

            for (String currentLine : lines) {
                currentLine = currentLine.trim();
                if (currentLine.isEmpty()) {
                    line++;
                    continue;
                }

                int i = 0;
                column = 1;

                while (i < currentLine.length()) {
                    char ch = currentLine.charAt(i);

                    if (Character.isWhitespace(ch)) {
                        column++;
                        i++;
                        continue;
                    }

                    if (i + 1 < currentLine.length()) {
                        String twoChars = currentLine.substring(i, i + 2);
                        if (RELATIONAL_OPERATORS.contains(twoChars) || LOGICAL_OPERATORS.contains(twoChars)) {
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
            return tokens;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    static class Parser {
        private List<Token> tokens;
        private int pos;
        private List<String> errors;

        public Parser(List<Token> tokens) {
            this.tokens = tokens;
            this.pos = 0;
            this.errors = new ArrayList<>();
        }

        public ASTNode parse() {
            ASTNode program = new ASTNode("Program");
            while (pos < tokens.size()) {
                ASTNode element = parseProgramElement();
                if (element != null) {
                    program.children.add(element);
                } else {
                    Token current = currentToken();
                    while (current != null && !";".equals(current.value)) {
                        pos++;
                        current = currentToken();
                    }
                    if (current != null && ";".equals(current.value)) {
                        pos++;
                    }
                }
            }
            return program;
        }

        private ASTNode parseProgramElement() {
            skipWhitespace();
            Token current = currentToken();
            if (current == null) return null;

            if ("KEYWORD".equals(current.type) && ("Double".equals(current.value) || "String".equals(current.value))) {
                return parseVariableDeclaration();
            } else if ("IDENTIFIER".equals(current.type)) {
                Token next = peekNextNonWhitespace(1);
                if (next != null && "(".equals(next.value)) {
                    return parseFunctionDefinition();
                }
                return parseAssignment();
            } else if ("KEYWORD".equals(current.type) && "call".equals(current.value)) {
                return parseFunctionCall();
            } else if ("NUMBER".equals(current.type) || "STRING".equals(current.type)) {
                int line = current.line;
                int column = current.column;
                String value = current.value;
                pos++;
                errors.add("Error at line " + line + ", column " + column +
                    ": Expected variable declaration, assignment, function definition, or 'call', found '" + value + "'");
                return null;
            }
            errors.add("Error at line " + current.line + ", column " + current.column +
                ": Expected variable declaration, assignment, function definition, or 'call', found '" + current.value + "'");
            return null;
        }

        private ASTNode parseVariableDeclaration() {
            Token typeToken = consume("KEYWORD");
            String varType = typeToken.value;
            skipWhitespace();
            Token idToken = consume("IDENTIFIER");
            if (idToken == null) {
                errors.add("Error at line " + typeToken.line + ", column " + typeToken.column +
                    ": Expected identifier after type in variable declaration");
                return null;
            }
            skipWhitespace();
            consume("OPERATOR", ";");

            ASTNode varDecl = new ASTNode("VariableDeclaration", idToken.value);
            varDecl.varType = varType;
            return varDecl;
        }

        private ASTNode parseFunctionDefinition() {
            ASTNode func = new ASTNode("Function");
            Token name = consume("IDENTIFIER");
            func.value = name.value;

            skipWhitespace();
            consume("OPERATOR", "(");
            skipWhitespace();
            ASTNode params = parseParameterList();
            func.children.add(params);
            skipWhitespace();
            consume("OPERATOR", ")");

            skipWhitespace();
            consume("OPERATOR", "{");
            while (currentToken() != null && !"}".equals(currentToken().value)) {
                ASTNode stmt = parseStatement();
                if (stmt != null) func.children.add(stmt);
                else pos++;
            }
            skipWhitespace();
            consume("OPERATOR", "}");
            return func;
        }

        private ASTNode parseParameterList() {
            ASTNode params = new ASTNode("Parameters");
            skipWhitespace();
            Token current = currentToken();
            if (current != null && !")".equals(current.value)) {
                Token param = consume("IDENTIFIER");
                if (param != null) {
                    ASTNode paramNode = new ASTNode("Parameter", param.value);
                    params.children.add(paramNode);
                }
                skipWhitespace();
                while (currentToken() != null && ",".equals(currentToken().value)) {
                    consume("OPERATOR", ",");
                    skipWhitespace();
                    param = consume("IDENTIFIER");
                    if (param != null) {
                        ASTNode paramNode = new ASTNode("Parameter", param.value);
                        params.children.add(paramNode);
                    }
                    skipWhitespace();
                }
            }
            return params;
        }

        private ASTNode parseStatement() {
            skipWhitespace();
            Token current = currentToken();
            if (current == null) return null;

            if ("KEYWORD".equals(current.type)) {
                if ("call".equals(current.value)) {
                    return parseFunctionCall();
                } else if ("return".equals(current.value)) {
                    ASTNode returnStmt = new ASTNode("Return");
                    consume("KEYWORD", "return");
                    skipWhitespace();
                    ASTNode expr = parseExpression();
                    if (expr != null) returnStmt.children.add(expr);
                    skipWhitespace();
                    consume("OPERATOR", ";");
                    return returnStmt;
                } else if ("if".equals(current.value)) {
                    return parseIfStatement();
                } else if ("for".equals(current.value)) {
                    return parseForStatement();
                } else if ("Double".equals(current.value) || "String".equals(current.value)) {
                    return parseVariableDeclaration();
                }
            } else if ("IDENTIFIER".equals(current.type)) {
                return parseAssignment();
            }
            errors.add("Error at line " + current.line + ", column " + current.column +
                ": Expected 'call', 'return', 'if', 'for', variable declaration, or assignment, found '" + current.value + "'");
            return null;
        }

        private ASTNode parseIfStatement() {
            consume("KEYWORD", "if");
            skipWhitespace();
            consume("OPERATOR", "(");
            skipWhitespace();
            ASTNode condition = parseExpression();
            skipWhitespace();
            consume("OPERATOR", ")");
            skipWhitespace();
            consume("OPERATOR", "{");
            ASTNode thenBlock = new ASTNode("ThenBlock");
            while (currentToken() != null && !"}".equals(currentToken().value)) {
                ASTNode stmt = parseStatement();
                if (stmt != null) thenBlock.children.add(stmt);
                else pos++;
            }
            skipWhitespace();
            consume("OPERATOR", "}");

            ASTNode ifNode = new ASTNode("If");
            ifNode.children.add(condition);
            ifNode.children.add(thenBlock);

            skipWhitespace();
            Token next = currentToken();
            if (next != null && "KEYWORD".equals(next.type) && "else".equals(next.value)) {
                consume("KEYWORD", "else");
                skipWhitespace();
                consume("OPERATOR", "{");
                ASTNode elseBlock = new ASTNode("ElseBlock");
                while (currentToken() != null && !"}".equals(currentToken().value)) {
                    ASTNode stmt = parseStatement();
                    if (stmt != null) elseBlock.children.add(stmt);
                    else pos++;
                }
                skipWhitespace();
                consume("OPERATOR", "}");
                ifNode.children.add(elseBlock);
            }
            return ifNode;
        }

        private ASTNode parseForStatement() {
            consume("KEYWORD", "for");
            skipWhitespace();
            consume("OPERATOR", "(");
            skipWhitespace();
            ASTNode init = parseAssignment();
            skipWhitespace();
            consume("OPERATOR", ";");
            skipWhitespace();
            ASTNode condition = parseExpression();
            skipWhitespace();
            consume("OPERATOR", ";");
            skipWhitespace();
            ASTNode update = parseAssignment();
            skipWhitespace();
            consume("OPERATOR", ")");
            skipWhitespace();
            consume("OPERATOR", "{");

            ASTNode body = new ASTNode("ForBody");
            while (currentToken() != null && !"}".equals(currentToken().value)) {
                ASTNode stmt = parseStatement();
                if (stmt != null) body.children.add(stmt);
                else pos++;
            }
            skipWhitespace();
            consume("OPERATOR", "}");

            ASTNode forNode = new ASTNode("For");
            forNode.children.add(init);
            forNode.children.add(condition);
            forNode.children.add(update);
            forNode.children.add(body);
            return forNode;
        }

        private ASTNode parseAssignment() {
            skipWhitespace();
            Token idToken = consume("IDENTIFIER");
            if (idToken == null) return null;

            skipWhitespace();
            Token next = currentToken();
            if (next != null && "=".equals(next.value)) {
                ASTNode stmt = new ASTNode("Assignment", idToken.value);
                consume("OPERATOR", "=");
                skipWhitespace();
                ASTNode expr = parseExpression();
                if (expr != null) {
                    stmt.children.add(expr);
                    skipWhitespace();
                    consume("OPERATOR", ";");
                    return stmt;
                } else {
                    errors.add("Error at line " + idToken.line + ", column " + idToken.column +
                        ": Expected expression after '=' in assignment");
                    return null;
                }
            }
            errors.add("Error at line " + idToken.line + ", column " + idToken.column +
                ": Expected '=' after identifier in assignment, found '" + (next != null ? next.value : "null") + "'");
            return null;
        }

        private ASTNode parseFunctionCall() {
            consume("KEYWORD", "call");
            skipWhitespace();
            Token idToken = consume("IDENTIFIER");
            ASTNode stmt = new ASTNode("FunctionCall", idToken.value);
            skipWhitespace();
            consume("OPERATOR", "(");
            skipWhitespace();
            ASTNode args = parseArgumentList();
            stmt.children.add(args);
            skipWhitespace();
            consume("OPERATOR", ")");
            skipWhitespace();
            consume("OPERATOR", ";");
            return stmt;
        }

        private ASTNode parseExpression() {
            return parseLogicalExpression();
        }

        private ASTNode parseLogicalExpression() {
            ASTNode left = parseComparisonExpression();
            skipWhitespace();
            Token current = currentToken();
            if (current != null && (current.value.equals("&&") || current.value.equals("||"))) {
                String op = consume("OPERATOR").value;
                skipWhitespace();
                ASTNode right = parseComparisonExpression();
                ASTNode logicalNode = new ASTNode("LogicalOp", op);
                logicalNode.children.add(left);
                logicalNode.children.add(right);
                return logicalNode;
            }
            return left;
        }

        private ASTNode parseComparisonExpression() {
            ASTNode left = parseAdditiveExpression();
            skipWhitespace();
            Token current = currentToken();
            if (current != null && ("==".equals(current.value) || "!=".equals(current.value) ||
                                    "<=".equals(current.value) || ">=".equals(current.value) ||
                                    "<".equals(current.value) || ">".equals(current.value))) {
                String op = consume("OPERATOR").value;
                skipWhitespace();
                ASTNode right = parseAdditiveExpression();
                ASTNode compNode = new ASTNode("Comparison", op);
                compNode.children.add(left);
                compNode.children.add(right);
                return compNode;
            }
            return left;
        }

        private ASTNode parseAdditiveExpression() {
            ASTNode left = parseMultiplicativeExpression();
            skipWhitespace();
            while (currentToken() != null && ("+".equals(currentToken().value) || "-".equals(currentToken().value))) {
                String op = consume("OPERATOR").value;
                skipWhitespace();
                ASTNode right = parseMultiplicativeExpression();
                ASTNode newNode = new ASTNode("BinaryOp", op);
                newNode.children.add(left);
                newNode.children.add(right);
                left = newNode;
                skipWhitespace();
            }
            return left;
        }

        private ASTNode parseMultiplicativeExpression() {
            ASTNode left = parseFactor();
            skipWhitespace();
            while (currentToken() != null && ("*".equals(currentToken().value) || "/".equals(currentToken().value) || "%".equals(currentToken().value))) {
                String op = consume("OPERATOR").value;
                skipWhitespace();
                ASTNode right = parseFactor();
                ASTNode newNode = new ASTNode("BinaryOp", op);
                newNode.children.add(left);
                newNode.children.add(right);
                left = newNode;
                skipWhitespace();
            }
            return left;
        }

        private ASTNode parseFactor() {
            skipWhitespace();
            Token current = currentToken();
            if (current == null) return null;

            if ("NUMBER".equals(current.type)) {
                return new ASTNode("Literal", consume("NUMBER").value);
            } else if ("STRING".equals(current.type)) {
                return new ASTNode("Literal", consume("STRING").value);
            } else if ("KEYWORD".equals(current.type) && "call".equals(current.value)) {
                consume("KEYWORD", "call");
                skipWhitespace();
                Token idToken = consume("IDENTIFIER");
                ASTNode call = new ASTNode("FunctionCall", idToken.value);
                skipWhitespace();
                consume("OPERATOR", "(");
                skipWhitespace();
                call.children.add(parseArgumentList());
                skipWhitespace();
                consume("OPERATOR", ")");
                return call;
            } else if ("IDENTIFIER".equals(current.type)) {
                String id = consume("IDENTIFIER").value;
                skipWhitespace();
                Token next = currentToken();
                if (next != null && "[".equals(next.value)) {
                    consume("OPERATOR", "[");
                    skipWhitespace();
                    ASTNode index = parseExpression();
                    skipWhitespace();
                    consume("OPERATOR", "]");
                    ASTNode node = new ASTNode("StringIndex");
                    node.children.add(new ASTNode("Variable", id));
                    node.children.add(index);
                    return node;
                }
                return new ASTNode("Variable", id);
            } else if ("(".equals(current.value)) {
                consume("OPERATOR", "(");
                skipWhitespace();
                ASTNode expr = parseExpression();
                skipWhitespace();
                consume("OPERATOR", ")");
                return expr;
            }
            return null;
        }

        private ASTNode parseArgumentList() {
            ASTNode args = new ASTNode("Arguments");
            skipWhitespace();
            Token current = currentToken();
            if (current != null && !")".equals(current.value)) {
                ASTNode expr = parseExpression();
                if (expr != null) args.children.add(expr);
                skipWhitespace();
                while (currentToken() != null && ",".equals(currentToken().value)) {
                    consume("OPERATOR", ",");
                    skipWhitespace();
                    expr = parseExpression();
                    if (expr != null) args.children.add(expr);
                    skipWhitespace();
                }
            }
            return args;
        }

        private void skipWhitespace() {
            while (pos < tokens.size() && Character.isWhitespace(tokens.get(pos).value.charAt(0))) {
                pos++;
            }
        }

        private Token peekNextNonWhitespace(int ahead) {
            int offset = 0;
            int count = 0;
            while (pos + offset < tokens.size()) {
                Token token = tokens.get(pos + offset);
                if (!Character.isWhitespace(token.value.charAt(0))) {
                    if (count == ahead) return token;
                    count++;
                }
                offset++;
            }
            return null;
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
            return null;
        }

        private Token consume(String expectedType, String expectedValue) {
            Token current = currentToken();
            if (current != null && expectedType.equals(current.type) && expectedValue.equals(current.value)) {
                pos++;
                return current;
            }
            return null;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    static class SemanticAnalyzer {
        private List<Token> tokens;
        private SymbolTable symbolTable;
        private List<String> errors;
        private static final Pattern NUMBER_PATTERN = Pattern.compile("[0-9]+(\\.[0-9]+)?");
        private static final Set<String> BUILT_IN_FUNCTIONS = new HashSet<>(Arrays.asList(
            "print", "length", "capitalize", "distance", "create_file", "delete_file", "copy_file", "move_file",
            "exec", "get_wd", "get_username", "get_user_home_dir", "change_dir", "get_env", "add", "subtract",
            "multiply", "divide", "max", "min", "abs", "compare", "factorial", "is_prime", "average", "round",
            "floor", "ceil", "is_even", "is_odd", "digit_sum", "is_palindrome", "reverse", "is_divisible",
            "modulus", "in_range", "random_num", "square", "cube", "percent_of", "roll_dice", "uppercase",
            "lowercase", "is_empty", "is_numeric", "concat", "contains", "index_of", "repeat_string", "is_positive", "is_greater"
        ));

        public SemanticAnalyzer(List<Token> tokens, SymbolTable symbolTable) {
            this.tokens = tokens;
            this.symbolTable = symbolTable;
            this.errors = new ArrayList<>();
            initializeBuiltInFunctions();
        }

        private void initializeBuiltInFunctions() {
            symbolTable.add("print", "Void", 0, 0);
            symbolTable.addFunctionParams("print", Collections.emptyList());
            symbolTable.add("length", "Double", 0, 0);
            symbolTable.addFunctionParams("length", Collections.singletonList("String"));
            symbolTable.add("capitalize", "String", 0, 0);
            symbolTable.addFunctionParams("capitalize", Collections.singletonList("String"));
            symbolTable.add("uppercase", "String", 0, 0);
            symbolTable.addFunctionParams("uppercase", Collections.singletonList("String"));
            symbolTable.add("lowercase", "String", 0, 0);
            symbolTable.addFunctionParams("lowercase", Collections.singletonList("String"));
            symbolTable.add("is_empty", "Boolean", 0, 0);
            symbolTable.addFunctionParams("is_empty", Collections.singletonList("String"));
            symbolTable.add("is_numeric", "Boolean", 0, 0);
            symbolTable.addFunctionParams("is_numeric", Collections.singletonList("String"));
            symbolTable.add("concat", "String", 0, 0);
            symbolTable.addFunctionParams("concat", Arrays.asList("String", "String"));
            symbolTable.add("contains", "Boolean", 0, 0);
            symbolTable.addFunctionParams("contains", Arrays.asList("String", "String"));
            symbolTable.add("index_of", "Double", 0, 0);
            symbolTable.addFunctionParams("index_of", Arrays.asList("String", "String"));
            symbolTable.add("repeat_string", "String", 0, 0);
            symbolTable.addFunctionParams("repeat_string", Arrays.asList("String", "Double"));
            symbolTable.add("reverse", "String", 0, 0);
            symbolTable.addFunctionParams("reverse", Collections.singletonList("String"));

            symbolTable.add("create_file", "Boolean", 0, 0);
            symbolTable.addFunctionParams("create_file", Collections.singletonList("String"));
            symbolTable.add("delete_file", "Boolean", 0, 0);
            symbolTable.addFunctionParams("delete_file", Collections.singletonList("String"));
            symbolTable.add("copy_file", "Boolean", 0, 0);
            symbolTable.addFunctionParams("copy_file", Arrays.asList("String", "String"));
            symbolTable.add("move_file", "Boolean", 0, 0);
            symbolTable.addFunctionParams("move_file", Arrays.asList("String", "String"));
            symbolTable.add("exec", "Void", 0, 0);
            symbolTable.addFunctionParams("exec", Collections.singletonList("String"));
            symbolTable.add("get_wd", "String", 0, 0);
            symbolTable.addFunctionParams("get_wd", Collections.emptyList());
            symbolTable.add("get_username", "String", 0, 0);
            symbolTable.addFunctionParams("get_username", Collections.emptyList());
            symbolTable.add("get_user_home_dir", "String", 0, 0);
            symbolTable.addFunctionParams("get_user_home_dir", Collections.emptyList());
            symbolTable.add("change_dir", "Void", 0, 0);
            symbolTable.addFunctionParams("change_dir", Collections.singletonList("String"));
            symbolTable.add("get_env", "String", 0, 0);
            symbolTable.addFunctionParams("get_env", Collections.singletonList("String"));

            symbolTable.add("add", "Double", 0, 0);
            symbolTable.addFunctionParams("add", Arrays.asList("Double", "Double"));
            symbolTable.add("subtract", "Double", 0, 0);
            symbolTable.addFunctionParams("subtract", Arrays.asList("Double", "Double"));
            symbolTable.add("multiply", "Double", 0, 0);
            symbolTable.addFunctionParams("multiply", Arrays.asList("Double", "Double"));
            symbolTable.add("divide", "Double", 0, 0);
            symbolTable.addFunctionParams("divide", Arrays.asList("Double", "Double"));
            symbolTable.add("max", "Double", 0, 0);
            symbolTable.addFunctionParams("max", Arrays.asList("Double", "Double"));
            symbolTable.add("min", "Double", 0, 0);
            symbolTable.addFunctionParams("min", Arrays.asList("Double", "Double"));
            symbolTable.add("abs", "Double", 0, 0);
            symbolTable.addFunctionParams("abs", Collections.singletonList("Double"));
            symbolTable.add("compare", "Boolean", 0, 0);
            symbolTable.addFunctionParams("compare", Arrays.asList("Double", "Double"));
            symbolTable.add("factorial", "Double", 0, 0);
            symbolTable.addFunctionParams("factorial", Collections.singletonList("Double"));
            symbolTable.add("is_prime", "Boolean", 0, 0);
            symbolTable.addFunctionParams("is_prime", Collections.singletonList("Double"));
            symbolTable.add("average", "Double", 0, 0);
            symbolTable.addFunctionParams("average", Arrays.asList("Double", "Double", "Double"));
            symbolTable.add("round", "Double", 0, 0);
            symbolTable.addFunctionParams("round", Collections.singletonList("Double"));
            symbolTable.add("floor", "Double", 0, 0);
            symbolTable.addFunctionParams("floor", Collections.singletonList("Double"));
            symbolTable.add("ceil", "Double", 0, 0);
            symbolTable.addFunctionParams("ceil", Collections.singletonList("Double"));
            symbolTable.add("is_even", "Boolean", 0, 0);
            symbolTable.addFunctionParams("is_even", Collections.singletonList("Double"));
            symbolTable.add("is_odd", "Boolean", 0, 0);
            symbolTable.addFunctionParams("is_odd", Collections.singletonList("Double"));
            symbolTable.add("digit_sum", "Double", 0, 0);
            symbolTable.addFunctionParams("digit_sum", Collections.singletonList("Double"));
            symbolTable.add("is_palindrome", "Boolean", 0, 0);
            symbolTable.addFunctionParams("is_palindrome", Collections.singletonList("String"));
            symbolTable.add("is_divisible", "Boolean", 0, 0);
            symbolTable.addFunctionParams("is_divisible", Arrays.asList("Double", "Double"));
            symbolTable.add("modulus", "Double", 0, 0);
            symbolTable.addFunctionParams("modulus", Arrays.asList("Double", "Double"));
            symbolTable.add("in_range", "Boolean", 0, 0);
            symbolTable.addFunctionParams("in_range", Arrays.asList("Double", "Double", "Double"));
            symbolTable.add("random_num", "Double", 0, 0);
            symbolTable.addFunctionParams("random_num", Arrays.asList("Double", "Double"));
            symbolTable.add("square", "Double", 0, 0);
            symbolTable.addFunctionParams("square", Collections.singletonList("Double"));
            symbolTable.add("cube", "Double", 0, 0);
            symbolTable.addFunctionParams("cube", Collections.singletonList("Double"));
            symbolTable.add("percent_of", "Double", 0, 0);
            symbolTable.addFunctionParams("percent_of", Arrays.asList("Double", "Double"));
            symbolTable.add("roll_dice", "Double", 0, 0);
            symbolTable.addFunctionParams("roll_dice", Collections.emptyList());
            symbolTable.add("distance", "Double", 0, 0);
            symbolTable.addFunctionParams("distance", Arrays.asList("Double", "Double", "Double", "Double"));
            symbolTable.add("is_positive", "Boolean", 0, 0);
            symbolTable.addFunctionParams("is_positive", Collections.singletonList("Double"));
            symbolTable.add("is_greater", "Boolean", 0, 0);
            symbolTable.addFunctionParams("is_greater", Arrays.asList("Double", "Double"));
        }

        public void analyze(ASTNode ast) {
            for (ASTNode node : ast.children) {
                if ("Function".equals(node.type)) {
                    analyzeFunction(node);
                } else if ("Assignment".equals(node.type)) {
                    analyzeAssignment(node);
                } else if ("FunctionCall".equals(node.type)) {
                    analyzeFunctionCall(node);
                } else if ("For".equals(node.type)) {
                    analyzeFor(node);
                } else if ("If".equals(node.type)) {
                    analyzeIf(node);
                } else if ("VariableDeclaration".equals(node.type)) {
                    analyzeVariableDeclaration(node);
                }
                analyzeExpressions(node);
            }
        }

        private void analyzeExpressions(ASTNode node) {
            for (ASTNode child : node.children) {
                if ("FunctionCall".equals(child.type)) {
                    analyzeFunctionCall(child);
                } else if ("BinaryOp".equals(child.type)) {
                    analyzeBinaryOp(child);
                } else if ("Comparison".equals(child.type)) {
                    analyzeComparison(child);
                }
                analyzeExpressions(child);
            }
        }

        private void analyzeFunction(ASTNode func) {
            String funcName = func.value;
            if (BUILT_IN_FUNCTIONS.contains(funcName)) {
                return;
            }

            Token nameToken = tokens.stream().filter(t -> t.value.equals(funcName) && "IDENTIFIER".equals(t.type)).findFirst().orElse(null);
            int line = nameToken != null ? nameToken.line : 0;
            int column = nameToken != null ? nameToken.column : 0;

            ASTNode params = func.children.get(0);
            List<String> paramTypes = new ArrayList<>();
            for (ASTNode param : params.children) {
                String paramType = determineParamType(funcName, param.value);
                symbolTable.add(param.value, paramType, line, column + funcName.length() + 2);
                paramTypes.add(paramType);
            }
            symbolTable.addFunctionParams(funcName, paramTypes);

            String returnType = determineReturnType(funcName, func);
            symbolTable.add(funcName, returnType, line, column);
        }

        private String determineParamType(String funcName, String paramName) {
            if (funcName.equals("concat") || funcName.equals("concat_strings") || funcName.equals("reverse_string") ||
                funcName.equals("reverse") || funcName.equals("uppercase") || funcName.equals("lowercase") ||
                funcName.equals("is_empty") || funcName.equals("is_numeric") || funcName.equals("create_file") ||
                funcName.equals("delete_file") || funcName.equals("copy_file") || funcName.equals("move_file") ||
                funcName.equals("get_wd") || funcName.equals("get_username") || funcName.equals("get_user_home_dir") ||
                funcName.equals("change_dir") || funcName.equals("get_env") || funcName.equals("contains") ||
                funcName.equals("index_of") || funcName.equals("repeat_string") || funcName.equals("capitalize")) {
                return "String";
            }
            return "Double";
        }

        private String determineReturnType(String funcName, ASTNode func) {
            if (funcName.equals("is_even") || funcName.equals("is_odd") || funcName.equals("is_prime") ||
                funcName.equals("is_palindrome") || funcName.equals("is_positive") || funcName.equals("is_empty") ||
                funcName.equals("is_numeric") || funcName.equals("create_file") || funcName.equals("delete_file") ||
                funcName.equals("copy_file") || funcName.equals("move_file") || funcName.equals("contains") ||
                funcName.equals("is_greater")) {
                return "Boolean";
            }
            if (funcName.equals("concat") || funcName.equals("concat_strings") || funcName.equals("reverse_string") ||
                funcName.equals("reverse") || funcName.equals("uppercase") || funcName.equals("lowercase") ||
                funcName.equals("get_wd") || funcName.equals("get_username") || funcName.equals("get_user_home_dir") ||
                funcName.equals("get_env") || funcName.equals("repeat_string") || funcName.equals("capitalize")) {
                return "String";
            }
            if (funcName.equals("length") || funcName.equals("index_of")) {
                return "Double";
            }

            String returnType = "Double";
            for (ASTNode stmt : func.children) {
                if ("Return".equals(stmt.type) && !stmt.children.isEmpty()) {
                    returnType = inferReturnType(stmt.children.get(0));
                    break;
                }
            }
            return returnType;
        }

        private void analyzeVariableDeclaration(ASTNode varDecl) {
            String varName = varDecl.value;
            String varType = varDecl.varType;
            Token idToken = tokens.stream().filter(t -> t.value.equals(varName) && "IDENTIFIER".equals(t.type)).findFirst().orElse(null);
            int line = idToken != null ? idToken.line : 0;
            int column = idToken != null ? idToken.column : 0;
            symbolTable.add(varName, varType, line, column);
        }

        private void analyzeAssignment(ASTNode assignment) {
            String varName = assignment.value;
            Token idToken = tokens.stream().filter(t -> t.value.equals(varName) && "IDENTIFIER".equals(t.type)).findFirst().orElse(null);
            int line = idToken != null ? idToken.line : 0;
            int column = idToken != null ? idToken.column : 0;
            ASTNode expr = assignment.children.get(0);
            String type = inferReturnType(expr);
            symbolTable.add(varName, type, line, column);
        }

        private void analyzeFunctionCall(ASTNode call) {
            Token idToken = tokens.stream().filter(t -> t.value.equals(call.value) && "IDENTIFIER".equals(t.type)).findFirst().orElse(null);
            int line = idToken != null ? idToken.line : 0;
            int column = idToken != null ? idToken.column : 0;

            String funcName = call.value;
            if (!symbolTable.contains(funcName) && !BUILT_IN_FUNCTIONS.contains(funcName)) {
                errors.add("Error at line " + line + ", column " + column +
                    ": Undefined function '" + funcName + "'");
                return;
            }

            List<String> expectedParamTypes = symbolTable.getFunctionParams(funcName);
            ASTNode args = call.children.get(0);

            if ("print".equals(funcName)) {
                for (int i = 0; i < args.children.size(); i++) {
                    String actualType = inferReturnType(args.children.get(i));
                    if (!actualType.equals("String") && !actualType.equals("Double")) {
                        errors.add("Error at line " + line + ", column " + column +
                            ": Argument " + (i + 1) + " of function 'print' expects type String or Double, but " + actualType + " was provided");
                    }
                }
                return;
            }

            if (expectedParamTypes.size() != args.children.size()) {
                errors.add("Error at line " + line + ", column " + column +
                    ": Function '" + funcName + "' expects " + expectedParamTypes.size() +
                    " arguments, but " + args.children.size() + " were provided");
                return;
            }

            for (int i = 0; i < args.children.size(); i++) {
                String actualType = inferReturnType(args.children.get(i));
                String expectedType = expectedParamTypes.get(i);
                if (!actualType.equals(expectedType)) {
                    errors.add("Error at line " + line + ", column " + column +
                        ": Argument " + (i + 1) + " of function '" + funcName +
                        "' expects type " + expectedType + ", but " + actualType + " was provided");
                }
            }
        }

        private void analyzeFor(ASTNode forNode) {
            ASTNode init = forNode.children.get(0);
            ASTNode condition = forNode.children.get(1);
            ASTNode update = forNode.children.get(2);
            ASTNode body = forNode.children.get(3);

            analyzeAssignment(init);
            String conditionType = inferReturnType(condition);
            if (!conditionType.equals("Boolean")) {
                Token token = tokens.stream().filter(t -> t.value.equals("for") && "KEYWORD".equals(t.type)).findFirst().orElse(null);
                int line = token != null ? token.line : 0;
                int column = token != null ? token.column : 0;
                errors.add("Error at line " + line + ", column " + column +
                    ": For loop condition must evaluate to Boolean, but got " + conditionType);
            }
            analyzeAssignment(update);
            for (ASTNode stmt : body.children) {
                if ("Assignment".equals(stmt.type)) {
                    analyzeAssignment(stmt);
                } else if ("FunctionCall".equals(stmt.type)) {
                    analyzeFunctionCall(stmt);
                } else if ("If".equals(stmt.type)) {
                    analyzeIf(stmt);
                } else if ("VariableDeclaration".equals(stmt.type)) {
                    analyzeVariableDeclaration(stmt);
                }
                analyzeExpressions(stmt);
            }
        }

        private void analyzeIf(ASTNode ifNode) {
            ASTNode condition = ifNode.children.get(0);
            String conditionType = inferReturnType(condition);
            if (!conditionType.equals("Boolean")) {
                Token token = tokens.stream().filter(t -> t.value.equals("if") && "KEYWORD".equals(t.type)).findFirst().orElse(null);
                int line = token != null ? token.line : 0;
                int column = token != null ? token.column : 0;
                errors.add("Error at line " + line + ", column " + column +
                    ": If condition must evaluate to Boolean, but got " + conditionType);
            }

            ASTNode thenBlock = ifNode.children.get(1);
            for (ASTNode stmt : thenBlock.children) {
                if ("Assignment".equals(stmt.type)) {
                    analyzeAssignment(stmt);
                } else if ("FunctionCall".equals(stmt.type)) {
                    analyzeFunctionCall(stmt);
                } else if ("For".equals(stmt.type)) {
                    analyzeFor(stmt);
                } else if ("If".equals(stmt.type)) {
                    analyzeIf(stmt);
                } else if ("VariableDeclaration".equals(stmt.type)) {
                    analyzeVariableDeclaration(stmt);
                } else if ("Return".equals(stmt.type)) {
                    // No additional analysis needed for return
                }
                analyzeExpressions(stmt);
            }

            if (ifNode.children.size() > 2) {
                ASTNode elseBlock = ifNode.children.get(2);
                for (ASTNode stmt : elseBlock.children) {
                    if ("Assignment".equals(stmt.type)) {
                        analyzeAssignment(stmt);
                    } else if ("FunctionCall".equals(stmt.type)) {
                        analyzeFunctionCall(stmt);
                    } else if ("For".equals(stmt.type)) {
                        analyzeFor(stmt);
                    } else if ("If".equals(stmt.type)) {
                        analyzeIf(stmt);
                    } else if ("VariableDeclaration".equals(stmt.type)) {
                        analyzeVariableDeclaration(stmt);
                    } else if ("Return".equals(stmt.type)) {
                        // No additional analysis needed for return
                    }
                    analyzeExpressions(stmt);
                }
            }
        }

        private void analyzeBinaryOp(ASTNode binaryOp) {
            String leftType = inferReturnType(binaryOp.children.get(0));
            String rightType = inferReturnType(binaryOp.children.get(1));
            String operator = binaryOp.value;
            Token token = tokens.stream().filter(t -> t.value.equals(operator) && "OPERATOR".equals(t.type)).findFirst().orElse(null);
            int line = token != null ? token.line : 0;
            int column = token != null ? token.column : 0;

            if (operator.equals("+")) {
                if (!(leftType.equals("Double") && rightType.equals("Double") ||
                      leftType.equals("String") || rightType.equals("String"))) {
                    errors.add("Error at line " + line + ", column " + column +
                        ": Operator '+' expects Double or String operands, but got " + leftType + " and " + rightType);
                }
            } else if (operator.equals("-") || operator.equals("*") || operator.equals("/")) {
                if (!leftType.equals("Double") || !rightType.equals("Double")) {
                    errors.add("Error at line " + line + ", column " + column +
                        ": Operator '" + operator + "' expects Double operands, but got " + leftType + " and " + rightType);
                }
            }
        }

        private void analyzeComparison(ASTNode comparison) {
            String leftType = inferReturnType(comparison.children.get(0));
            String rightType = inferReturnType(comparison.children.get(1));
            String operator = comparison.value;
            Token token = tokens.stream().filter(t -> t.value.equals(operator) && "OPERATOR".equals(t.type)).findFirst().orElse(null);
            int line = token != null ? token.line : 0;
            int column = token != null ? token.column : 0;

            if (!leftType.equals("Double") || !rightType.equals("Double")) {
                errors.add("Error at line " + line + ", column " + column +
                    ": Comparison operator '" + operator + "' expects Double operands, but got " + leftType + " and " + rightType);
            }
        }

        private String inferReturnType(ASTNode expr) {
            if (expr == null) return "Double";
            if ("Literal".equals(expr.type)) {
                String value = expr.value;
                if (value.startsWith("\"") && value.endsWith("\"")) return "String";
                if (value.equals("true") || value.equals("false")) return "Boolean";
                if (NUMBER_PATTERN.matcher(value).matches()) return "Double";
                return "Unknown";
            }
            if ("Variable".equals(expr.type)) {
                String type = symbolTable.getType(expr.value);
                if (type == null) {
                    Token token = tokens.stream().filter(t -> t.value.equals(expr.value) && "IDENTIFIER".equals(t.type)).findFirst().orElse(null);
                    int line = token != null ? token.line : 0;
                    int column = token != null ? token.column : 0;
                    errors.add("Error at line " + line + ", column " + column +
                        ": Undefined variable '" + expr.value + "'");
                    return "Unknown";
                }
                return type;
            }
            if ("FunctionCall".equals(expr.type)) {
                String type = symbolTable.getType(expr.value);
                if (type == null) {
                    Token token = tokens.stream().filter(t -> t.value.equals(expr.value) && "IDENTIFIER".equals(t.type)).findFirst().orElse(null);
                    int line = token != null ? token.line : 0;
                    int column = token != null ? token.column : 0;
                    errors.add("Error at line " + line + ", column " + column +
                        ": Undefined function '" + expr.value + "'");
                    return "Unknown";
                }
                return type;
            }
            if ("StringIndex".equals(expr.type)) return "String";
            if ("BinaryOp".equals(expr.type)) {
                String leftType = inferReturnType(expr.children.get(0));
                String rightType = inferReturnType(expr.children.get(1));
                String operator = expr.value;
                if (operator.equals("+") && (leftType.equals("String") || rightType.equals("String"))) return "String";
                if (leftType.equals("Double") && rightType.equals("Double")) return "Double";
                return "Unknown";
            }
            if ("Comparison".equals(expr.type)) return "Boolean";
            return "Double";
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    static class CodeGenerator {
        private SymbolTable symbolTable;
        private static final Pattern NUMBER_PATTERN = Pattern.compile("[0-9]+(\\.[0-9]+)?");
        private Set<String> generatedFunctions = new HashSet<>();

        public CodeGenerator(SymbolTable symbolTable) {
            this.symbolTable = symbolTable;
        }

        public String generateCode(ASTNode ast) {
            StringBuilder output = new StringBuilder();
            output.append("import java.io.*;\n");
            output.append("import java.nio.file.*;\n");
            output.append("import java.util.*;\n");
            output.append("public class CompilerOutput {\n");

            for (ASTNode element : ast.children) {
                if ("Function".equals(element.type)) {
                    String returnType = symbolTable.getType(element.value);
                    output.append("    public static ").append(returnType).append(" ")
                        .append(element.value).append("(");

                    ASTNode params = element.children.get(0);
                    List<String> paramTypes = symbolTable.getFunctionParams(element.value);
                    for (int i = 0; i < params.children.size(); i++) {
                        if (i > 0) output.append(", ");
                        output.append(paramTypes.get(i)).append(" ").append(params.children.get(i).value);
                    }
                    output.append(") {\n");

                    Map<String, String> locals = new HashMap<>();
                    for (int i = 1; i < element.children.size(); i++) {
                        collectLocalVariables(element.children.get(i), locals);
                    }
                    for (Map.Entry<String, String> entry : locals.entrySet()) {
                        output.append("        ").append(entry.getValue()).append(" ").append(entry.getKey()).append(";\n");
                    }

                    for (int i = 1; i < element.children.size(); i++) {
                        output.append("        ").append(generateStatement(element.children.get(i)));
                    }
                    output.append("    }\n\n");
                }
            }

            output.append(generateBuiltInFunctions(ast));
            output.append(generateHelperMethods());

            output.append("    public static void main(String[] args) throws Exception {\n");

            List<String> mainStatements = new ArrayList<>();
            Map<String, String> variables = new HashMap<>();

            for (ASTNode element : ast.children) {
                if ("VariableDeclaration".equals(element.type)) {
                    String varName = element.value;
                    String type = symbolTable.getType(varName);
                    variables.put(varName, type);
                    mainStatements.add(type + " " + varName + ";");
                } else if ("Assignment".equals(element.type)) {
                    String varName = element.value;
                    String type = symbolTable.getType(varName);
                    variables.put(varName, type);
                    String expr = generateExpression(element.children.get(0));
                    if (!mainStatements.contains(type + " " + varName + ";")) {
                        mainStatements.add(type + " " + varName + ";");
                    }
                    mainStatements.add(varName + " = " + expr + ";");
                } else if ("FunctionCall".equals(element.type)) {
                    if ("print".equals(element.value)) {
                        ASTNode args = element.children.get(0);
                        if (args.children.isEmpty()) {
                            mainStatements.add("System.out.println();");
                        } else {
                            StringBuilder printArgs = new StringBuilder();
                            for (int i = 0; i < args.children.size(); i++) {
                                if (i > 0) printArgs.append(" + \" \" + ");
                                printArgs.append(generateExpression(args.children.get(i)));
                            }
                            mainStatements.add("System.out.println(" + printArgs + ");");
                        }
                    } else {
                        mainStatements.add(generateExpression(element) + ";");
                    }
                }
            }

            for (String stmt : mainStatements) {
                output.append("        ").append(stmt).append("\n");
            }

            output.append("    }\n");
            output.append("}\n");
            return output.toString();
        }

        private String generateBuiltInFunctions(ASTNode ast) {
            StringBuilder funcs = new StringBuilder();
            Set<String> usedFunctions = new HashSet<>();
            collectUsedFunctions(ast, usedFunctions);

            if (usedFunctions.contains("add")) {
                funcs.append("    public static Double add(Double a, Double b) {\n");
                funcs.append("        return a + b;\n");
                funcs.append("    }\n\n");
                generatedFunctions.add("add");
            }
            if (usedFunctions.contains("multiply")) {
                funcs.append("    public static Double multiply(Double a, Double b) {\n");
                funcs.append("        return a * b;\n");
                funcs.append("    }\n\n");
                generatedFunctions.add("multiply");
            }
            if (usedFunctions.contains("concat")) {
                funcs.append("    public static String concat(String s1, String s2) {\n");
                funcs.append("        return s1 + s2;\n");
                funcs.append("    }\n\n");
                generatedFunctions.add("concat");
            }
            if (usedFunctions.contains("is_positive")) {
                funcs.append("    public static Boolean is_positive(Double n) {\n");
                funcs.append("        return n > 0.0;\n");
                funcs.append("    }\n\n");
                generatedFunctions.add("is_positive");
            }
            if (usedFunctions.contains("is_greater")) {
                funcs.append("    public static Boolean is_greater(Double x, Double y) {\n");
                funcs.append("        return x > y;\n");
                funcs.append("    }\n\n");
                generatedFunctions.add("is_greater");
            }
            if (usedFunctions.contains("square")) {
                funcs.append("    public static Double square(Double a) {\n");
                funcs.append("        return Math.pow(a, 2);\n");
                funcs.append("    }\n\n");
                generatedFunctions.add("square");
            }
            if (usedFunctions.contains("roll_dice")) {
                funcs.append("    public static Double roll_dice() {\n");
                funcs.append("        return (double)(new Random().nextInt(6) + 1);\n");
                funcs.append("    }\n\n");
                generatedFunctions.add("roll_dice");
            }
            if (usedFunctions.contains("length")) {
                funcs.append("    public static Double length(String s) {\n");
                funcs.append("        return (double)s.length();\n");
                funcs.append("    }\n\n");
                generatedFunctions.add("length");
            }
            if (usedFunctions.contains("uppercase")) {
                funcs.append("    public static String uppercase(String s) {\n");
                funcs.append("        return s.toUpperCase();\n");
                funcs.append("    }\n\n");
                generatedFunctions.add("uppercase");
            }
            if (usedFunctions.contains("get_username")) {
                funcs.append("    public static String get_username() {\n");
                funcs.append("        return System.getProperty(\"user.name\");\n");
                funcs.append("    }\n\n");
                generatedFunctions.add("get_username");
            }
            if (usedFunctions.contains("get_user_home_dir")) {
                funcs.append("    public static String get_user_home_dir() {\n");
                funcs.append("        return System.getProperty(\"user.home\");\n");
                funcs.append("    }\n\n");
                generatedFunctions.add("get_user_home_dir");
            }
            if (usedFunctions.contains("create_file")) {
                funcs.append("    public static Boolean create_file(String path) throws IOException {\n");
                funcs.append("        return new File(path).createNewFile();\n");
                funcs.append("    }\n\n");
                generatedFunctions.add("create_file");
            }
            return funcs.toString();
        }

        private void collectUsedFunctions(ASTNode node, Set<String> usedFunctions) {
            if ("FunctionCall".equals(node.type)) {
                usedFunctions.add(node.value);
            }
            for (ASTNode child : node.children) {
                collectUsedFunctions(child, usedFunctions);
            }
        }

        private void collectLocalVariables(ASTNode node, Map<String, String> locals) {
            if ("Assignment".equals(node.type)) {
                String varName = node.value;
                if (!locals.containsKey(varName)) {
                    locals.put(varName, symbolTable.getType(varName));
                }
            } else if ("VariableDeclaration".equals(node.type)) {
                String varName = node.value;
                if (!locals.containsKey(varName)) {
                    locals.put(varName, symbolTable.getType(varName));
                }
            } else if ("For".equals(node.type)) {
                collectLocalVariables(node.children.get(0), locals);
                collectLocalVariables(node.children.get(3), locals);
                collectLocalVariables(node.children.get(2), locals);
            } else if ("If".equals(node.type)) {
                collectLocalVariables(node.children.get(1), locals);
                if (node.children.size() > 2) {
                    collectLocalVariables(node.children.get(2), locals);
                }
            }
        }

        private String generateStatement(ASTNode stmt) {
            if ("Return".equals(stmt.type)) {
                return "return " + (stmt.children.isEmpty() ? "" : generateExpression(stmt.children.get(0))) + ";\n";
            } else if ("Assignment".equals(stmt.type)) {
                return stmt.value + " = " + generateExpression(stmt.children.get(0)) + ";\n";
            } else if ("VariableDeclaration".equals(stmt.type)) {
                return symbolTable.getType(stmt.value) + " " + stmt.value + ";\n";
            } else if ("If".equals(stmt.type)) {
                StringBuilder sb = new StringBuilder();
                sb.append("if (").append(generateExpression(stmt.children.get(0))).append(") {\n");
                for (ASTNode thenStmt : stmt.children.get(1).children) {
                    sb.append("            ").append(generateStatement(thenStmt));
                }
                sb.append("        }");
                if (stmt.children.size() > 2) {
                    sb.append(" else {\n");
                    for (ASTNode elseStmt : stmt.children.get(2).children) {
                        sb.append("            ").append(generateStatement(elseStmt));
                    }
                    sb.append("        }");
                }
                sb.append("\n");
                return sb.toString();
            } else if ("For".equals(stmt.type)) {
                StringBuilder sb = new StringBuilder();
                sb.append("for (");
                sb.append(symbolTable.getType(stmt.children.get(0).value)).append(" ").append(stmt.children.get(0).value).append(" = ").append(generateExpression(stmt.children.get(0).children.get(0)));
                sb.append("; ").append(generateExpression(stmt.children.get(1)));
                sb.append("; ").append(stmt.children.get(2).value).append(" = ").append(generateExpression(stmt.children.get(2).children.get(0)));
                sb.append(") {\n");
                for (ASTNode bodyStmt : stmt.children.get(3).children) {
                    sb.append("            ").append(generateStatement(bodyStmt));
                }
                sb.append("        }\n");
                return sb.toString();
            } else if ("FunctionCall".equals(stmt.type)) {
                if ("print".equals(stmt.value)) {
                    ASTNode args = stmt.children.get(0);
                    if (args.children.isEmpty()) {
                        return "System.out.println();\n";
                    } else {
                        StringBuilder printArgs = new StringBuilder();
                        for (int i = 0; i < args.children.size(); i++) {
                            if (i > 0) printArgs.append(" + \" \" + ");
                            printArgs.append(generateExpression(args.children.get(i)));
                        }
                        return "System.out.println(" + printArgs + ");\n";
                    }
                }
                return generateExpression(stmt) + ";\n";
            }
            return "";
        }

        private String generateExpression(ASTNode expr) {
            if ("Literal".equals(expr.type)) {
                String value = expr.value;
                if (NUMBER_PATTERN.matcher(value).matches() && !value.contains(".")) {
                    return value + ".0";
                }
                return value;
            } else if ("Variable".equals(expr.type)) {
                return expr.value;
            } else if ("StringIndex".equals(expr.type)) {
                return generateExpression(expr.children.get(0)) + ".charAt((int)" + generateExpression(expr.children.get(1)) + ")";
            } else if ("BinaryOp".equals(expr.type)) {
                return generateExpression(expr.children.get(0)) + " " + expr.value + " " + generateExpression(expr.children.get(1));
            } else if ("Comparison".equals(expr.type)) {
                return generateExpression(expr.children.get(0)) + " " + expr.value + " " + generateExpression(expr.children.get(1));
            } else if ("FunctionCall".equals(expr.type)) {
                String funcName = expr.value;
                StringBuilder args = new StringBuilder();
                for (int i = 0; i < expr.children.get(0).children.size(); i++) {
                    if (i > 0) args.append(", ");
                    args.append(generateExpression(expr.children.get(0).children.get(i)));
                }
                if ("add".equals(funcName)) {
                    return "add(" + args + ")";
                } else if ("multiply".equals(funcName)) {
                    return "multiply(" + args + ")";
                } else if ("concat".equals(funcName)) {
                    return "concat(" + args + ")";
                } else if ("is_positive".equals(funcName)) {
                    return "is_positive(" + args + ")";
                } else if ("is_greater".equals(funcName)) {
                    return "is_greater(" + args + ")";
                } else if ("square".equals(funcName)) {
                    return "square(" + args + ")";
                } else if ("roll_dice".equals(funcName)) {
                    return "roll_dice()";
                } else if ("length".equals(funcName)) {
                    return "length(" + args + ")";
                } else if ("uppercase".equals(funcName)) {
                    return "uppercase(" + args + ")";
                } else if ("get_username".equals(funcName)) {
                    return "get_username()";
                } else if ("get_user_home_dir".equals(funcName)) {
                    return "get_user_home_dir()";
                } else if ("create_file".equals(funcName)) {
                    return "create_file(" + args + ")";
                }
                return funcName + "(" + args.toString() + ")";
            }
            return "";
        }

        private String generateHelperMethods() {
            StringBuilder helpers = new StringBuilder();
            helpers.append("    private static long factorial(int n) {\n");
            helpers.append("        if (n <= 1) return 1;\n");
            helpers.append("        return n * factorial(n - 1);\n");
            helpers.append("    }\n\n");

            helpers.append("    private static boolean isPrime(int n) {\n");
            helpers.append("        if (n <= 1) return false;\n");
            helpers.append("        for (int i = 2; i <= Math.sqrt(n); i++) {\n");
            helpers.append("            if (n % i == 0) return false;\n");
            helpers.append("        }\n");
            helpers.append("        return true;\n");
            helpers.append("    }\n\n");

            helpers.append("    private static int digitSum(int n) {\n");
            helpers.append("        int sum = 0;\n");
            helpers.append("        while (n > 0) {\n");
            helpers.append("            sum += n % 10;\n");
            helpers.append("            n /= 10;\n");
            helpers.append("        }\n");
            helpers.append("        return sum;\n");
            helpers.append("    }\n\n");

            helpers.append("    private static boolean isPalindrome(String s) {\n");
            helpers.append("        int left = 0, right = s.length() - 1;\n");
            helpers.append("        while (left < right) {\n");
            helpers.append("            if (s.charAt(left++) != s.charAt(right--)) return false;\n");
            helpers.append("        }\n");
            helpers.append("        return true;\n");
            helpers.append("    }\n\n");

            helpers.append("    private static boolean isNumeric(String s) {\n");
            helpers.append("        try {\n");
            helpers.append("            Double.parseDouble(s);\n");
            helpers.append("            return true;\n");
            helpers.append("        } catch (NumberFormatException e) {\n");
            helpers.append("            return false;\n");
            helpers.append("        }\n");
            helpers.append("    }\n\n");

            helpers.append("    private static String repeatString(String s, int count) {\n");
            helpers.append("        StringBuilder sb = new StringBuilder();\n");
            helpers.append("        for (int i = 0; i < count; i++) {\n");
            helpers.append("            sb.append(s);\n");
            helpers.append("        }\n");
            helpers.append("        return sb.toString();\n");
            helpers.append("    }\n");

            return helpers.toString();
        }
    }

    public String compile() {
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.tokenize();
        List<String> lexerErrors = lexer.getErrors();

        Parser parser = new Parser(tokens);
        ASTNode ast = parser.parse();
        List<String> parserErrors = parser.getErrors();

        SymbolTable symbolTable = new SymbolTable();
        SemanticAnalyzer analyzer = new SemanticAnalyzer(tokens, symbolTable);
        analyzer.analyze(ast);
        List<String> analyzerErrors = analyzer.getErrors();

        List<String> allErrors = new ArrayList<>();
        allErrors.addAll(lexerErrors);
        allErrors.addAll(parserErrors);
        allErrors.addAll(analyzerErrors);

        if (!allErrors.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder("Compilation failed due to the following errors:\n");
            for (String error : allErrors) {
                errorMessage.append(error).append("\n");
            }
            return errorMessage.toString();
        }

        CodeGenerator codeGenerator = new CodeGenerator(symbolTable);
        return codeGenerator.generateCode(ast);
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

            if (javaCode.contains("Compilation failed")) {
                System.err.println(javaCode);
                System.exit(1);
            }

            writeFile(outputFile, javaCode);
            System.out.println("Compilation complete. Output written to " + outputFile);
            System.out.println(javaCode);

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
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
}
