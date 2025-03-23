import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Lexer {
    private static final String[] KEYWORDS = {"if", "else", "for", "return", "call"};
    private static final String SINGLE_OPERATORS = "+-*/%=(){}<>;,.";
    private static final String RELATIONAL_OPERATORS = "== != <= >= < >";
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[0-9]+(\\.[0-9]+)?");
    private static final Pattern STRING_PATTERN = Pattern.compile("\"[^\"]*\"");

    private String input;
    private List<Token> tokens;
    private List<String> errors;
    private ErrorHandler errorHandler;

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
        return tokens;
    }

    public List<String> getErrors() {
        return errors;
    }
}