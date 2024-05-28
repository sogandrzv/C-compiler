import java.util.ArrayList;

public class Variables {
    ArrayList<Variables> children;
    ArrayList<Var> vars;

    public Variables() {
        this.children = new ArrayList<>();
        this.vars = new ArrayList<>();
    }

    public static Variables get_parent(Variables root, Variables v) {
        if (root == null || v == null) {
            return null;
        }
        for (Variables child : root.children) {
            if (child == v) {
                return root;
            }
            else {
                Variables parent = get_parent(child, v);
                if (parent != null) {
                    return parent;
                }
            }
        }
        return null;
    }
}
