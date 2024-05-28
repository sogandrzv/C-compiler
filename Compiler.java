import gen.CLexer;
import gen.CListener;
import gen.CParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.IOException;

public class Compiler {
    public static void main(String[] args) throws IOException {
        CharStream stream = CharStreams.fromFileName("sample/test.c");
        CLexer lexer = new CLexer(stream);
        TokenStream tokens = new CommonTokenStream(lexer);
        CParser parser = new CParser(tokens);
        ParseTree tree = parser.externalDeclaration();
        ParseTreeWalker walker = new ParseTreeWalker();
//        CListener listener = new Phase1();
        CListener listener = new Phase2();

        walker.walk(listener, tree);
    }
}
