import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;

public class SymbolTable {
    String name;
    int line_num;
    ArrayList<SymbolTable> children;
    Hashtable<String, String> table;

    public SymbolTable(String name, int line_num) {
        this.name =  name;
        this.line_num = line_num;
        this.children = new ArrayList<>();
        this.table = new Hashtable<>();
    }

    public void print_table() {
        Iterator<String> itr = table.keySet().iterator();
        System.out.println("-".repeat(9) + " " + this.name + ": " + line_num + " " + "-".repeat(9));
        while(itr.hasNext()){
            String key = itr.next();
            System.out.print("Key: " + key + " | ");
            System.out.println("Value: " + table.get(key));
        }
        System.out.println("=".repeat(50));
    }

    public static void print_all_tables(SymbolTable root) {
        root.print_table();
        for (int i = 0; i < root.children.size(); i++) {
            print_all_tables(root.children.get(i));
        }
    }

    public static SymbolTable get_parent(SymbolTable root, SymbolTable s) {
        if (root == null || s == null) {
            return null;
        }
        for (SymbolTable child : root.children) {
            if (child == s) {
                return root;
            }
            else {
                SymbolTable parent = get_parent(child, s);
                if (parent != null) {
                    return parent;
                }
            }
        }
        return null;
    }
}
