import java.util.ArrayList;
import java.util.List;

public class ASTNode {
    String type;
    List<ASTNode> children;
    String value;

    ASTNode(String type) {
        this.type = type;
        this.children = new ArrayList<>();
    }
}