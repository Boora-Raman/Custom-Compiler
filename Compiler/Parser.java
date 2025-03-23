import java.util.*;

public class Parser {
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
                pos++;
            }
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
            return parseAssignment();
        } else if ("KEYWORD".equals(current.type) && "call".equals(current.value)) {
            return parseFunctionCall();
        }
        errors.add("Error at line " + current.line + ", column " + current.column +
            ": Expected assignment, function definition, or 'call', found '" + current.value + "'");
        return null;
    }

    private ASTNode parseFunctionDefinition() {
        ASTNode func = new ASTNode("Function");
        Token name = consume("IDENTIFIER");
        func.value = name.value;

        consume("OPERATOR", "(");
        ASTNode params = parseParameterList();
        func.children.add(params);
        consume("OPERATOR", ")");

        consume("OPERATOR", "{");
        while (currentToken() != null && !"}".equals(currentToken().value)) {
            ASTNode stmt = parseStatement();
            if (stmt != null) func.children.add(stmt);
            else break;
        }
        consume("OPERATOR", "}");
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
            }
            while (currentToken() != null && ",".equals(currentToken().value)) {
                consume("OPERATOR", ",");
                param = consume("IDENTIFIER");
                if (param != null) {
                    ASTNode paramNode = new ASTNode("Parameter");
                    paramNode.value = param.value;
                    params.children.add(paramNode);
                }
            }
        }
        return params;
    }

    private ASTNode parseStatement() {
        Token current = currentToken();
        if (current == null) return null;

        if ("KEYWORD".equals(current.type)) {
            if ("call".equals(current.value)) {
                return parseFunctionCall();
            } else if ("return".equals(current.value)) {
                ASTNode returnStmt = new ASTNode("Return");
                consume("KEYWORD", "return");
                ASTNode expr = parseExpression();
                if (expr != null) returnStmt.children.add(expr);
                consume("OPERATOR", ";");
                return returnStmt;
            } else if ("if".equals(current.value)) {
                return parseIfStatement();
            }
        } else if ("IDENTIFIER".equals(current.type)) {
            return parseAssignment();
        }
        errors.add("Error at line " + current.line + ", column " + current.column +
            ": Expected 'call', 'return', 'if', or assignment, found '" + current.value + "'");
        return null;
    }

    private ASTNode parseIfStatement() {
        Token ifToken = consume("KEYWORD", "if");
        if (ifToken == null) return null;

        consume("OPERATOR", "(");
        ASTNode condition = parseComparisonExpression();
        if (condition == null) {
            errors.add("Error at line " + ifToken.line + ", column " + ifToken.column +
                ": Expected condition in 'if' statement");
            return null;
        }
        consume("OPERATOR", ")");

        consume("OPERATOR", "{");
        ASTNode thenBlock = new ASTNode("ThenBlock");
        while (currentToken() != null && !"}".equals(currentToken().value)) {
            ASTNode stmt = parseStatement();
            if (stmt != null) thenBlock.children.add(stmt);
            else break;
        }
        consume("OPERATOR", "}");

        ASTNode ifNode = new ASTNode("If");
        ifNode.children.add(condition);
        ifNode.children.add(thenBlock);
        return ifNode;
    }

    private ASTNode parseAssignment() {
        Token idToken = consume("IDENTIFIER");
        if (idToken == null) return null;

        Token next = currentToken();
        if (next != null && "=".equals(next.value)) {
            ASTNode stmt = new ASTNode("Assignment");
            stmt.value = idToken.value;
            consume("OPERATOR", "=");
            ASTNode expr = parseExpression();
            if (expr == null) {
                errors.add("Error at line " + idToken.line + ", column " + (idToken.column + 1) +
                    ": Expected expression after '='");
                return null;
            }
            stmt.children.add(expr);
            Token semicolon = consume("OPERATOR", ";");
            if (semicolon == null) {
                errors.add("Error at line " + idToken.line + ", column " + (idToken.column + 1) +
                    ": Expected ';' after assignment");
                return null;
            }
            return stmt;
        }
        errors.add("Error at line " + idToken.line + ", column " + idToken.column +
            ": Expected '=' after identifier '" + idToken.value + "' in assignment");
        return null;
    }

    private ASTNode parseFunctionCall() {
        Token callToken = consume("KEYWORD", "call");
        if (callToken == null) return null;

        Token idToken = consume("IDENTIFIER");
        if (idToken == null) {
            errors.add("Error at line " + callToken.line + ", column " + callToken.column +
                ": Expected identifier after 'call'");
            return null;
        }
        ASTNode stmt = new ASTNode("FunctionCall");
        stmt.value = idToken.value;
        consume("OPERATOR", "(");
        ASTNode args = parseArgumentList();
        stmt.children.add(args);
        consume("OPERATOR", ")");
        consume("OPERATOR", ";");
        return stmt;
    }

    private ASTNode parseExpression() {
        return parseComparisonExpression();
    }

    private ASTNode parseComparisonExpression() {
        ASTNode left = parseAdditiveExpression();
        Token current = currentToken();
        if (current != null && "== != <= >= < >".contains(current.value)) {
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
        while (currentToken() != null && ("*".equals(currentToken().value) || "/".equals(currentToken().value))) {
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
                errors.add("Error at line " + current.line + ", column " + current.column +
                    ": Expected identifier after 'call'");
                return null;
            }
            ASTNode call = new ASTNode("FunctionCall");
            call.value = idToken.value;
            consume("OPERATOR", "(");
            call.children.add(parseArgumentList());
            consume("OPERATOR", ")");
            return call;
        } else if ("IDENTIFIER".equals(current.type)) {
            String id = consume("IDENTIFIER").value;
            ASTNode node = new ASTNode("Variable");
            node.value = id;
            return node;
        } else if ("(".equals(current.value)) {
            consume("OPERATOR", "(");
            ASTNode expr = parseExpression();
            consume("OPERATOR", ")");
            return expr;
        }
        errors.add("Error at line " + current.line + ", column " + current.column +
            ": Invalid expression factor '" + current.value + "'");
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

    public List<String> getErrors() {
        return errors;
    }
}