public class Token {
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