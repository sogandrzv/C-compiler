import gen.CListener;
import gen.CParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Phase2 implements CListener {

    SymbolTable program;  // main symbol table, root of the tree structure of symbol tables
    Stack<SymbolTable> stack = new Stack<>();  // used for keeping track of symbol tables
    Variables variables_root;  // main data structure used for storing variables throughout the program
    Stack<Variables> variables_stack = new Stack<>();  // variable stack

    String return_type = null;  // store return type of the most recent function
    boolean return_type_match = false;

    /* ERROR CHECKING METHODS */

    boolean check_if_duplicate(String name, Hashtable<String, String> table) {
        // check if method or field already in symbol table
        for (String keyString : table.keySet()) {
            if (keyString.equals("Method_" + name) || keyString.equals("Field_" + name))
                return true;
        }
        return false;
    }

    int get_column(ParserRuleContext ctx) {
        // return column where a variable is located
        // given "i", look for parent that contains whole line or for example "int i;"
        if (ctx == null) return -1;
        String name = ctx.getText();
        RuleContext p = ctx.parent;
        int col_num = -1;
        while (p != null && !(p instanceof CParser.StatementContext) && !(p instanceof CParser.DeclarationContext)) {
            p = p.parent;
        }
        if (p instanceof CParser.StatementContext || p instanceof CParser.DeclarationContext || p instanceof CParser.BlockItemContext) {
            String temp = p.getText();
            col_num = temp.indexOf(name);
        }
        return col_num;
    }

    public Hashtable<String, String> all_defined_variables(SymbolTable s) {
        // return list of all defined variables stored in given symbol table "s": name and datatype
        Hashtable<String, String> result = new Hashtable<>();

        for (String value : s.table.values()) {
            // only need method fields (not parameter fields)
            String variable_type = value.substring(0, value.indexOf("(") - 1);
            if (!variable_type.equals("methodField")) continue;

            // get variable name
            int start_index = value.indexOf("name:") + 6;
            int end_index = value.indexOf(")", start_index);
            String name = value.substring(start_index, end_index);

            // get variable datatype
            start_index = value.indexOf("(type:") + 7;
            end_index = value.indexOf(")", start_index);
            String datatype = value.substring(start_index, end_index);

            // add to hashtable
            result.put(name, datatype);
        }
        return result;
    }

    public Hashtable<String, String> defined_in_all_scopes(SymbolTable s) {
        // using method "all_defined_variables()", return list of variables defined in this scope, or scopes of ancestors

        // variables in this scope
        Hashtable<String, String> result = new Hashtable<>(all_defined_variables(s));

        // variables in ancestors scopes
        SymbolTable parent = SymbolTable.get_parent(program, s);
        while (parent != null && parent != program) {
            result.putAll(all_defined_variables(parent));
            parent = SymbolTable.get_parent(program, parent);
        }
        return result;
    }

    public void check_for_not_defined(ArrayList<Var> used_variables, Hashtable<String, String> defined_variables) {
        // given list of defined variables and used variables,
        // check if there is any used variable not stored in the defined variables list

        boolean defined;
        for (Var v : used_variables) {
            defined = false;
            for (String x : defined_variables.keySet()) {  // iterate over keys of hashtable (names)
                if (v.name.equals(x)) {
                    defined = true;
                    break;
                }
            }
            if (!defined) {
                // print error line
                String result = "Error106 : in line " + v.line_num + ":" + v.col_num + "," +
                        " Can not find Variable " + v.name;
                System.out.println(result);
            }
        }
    }

    @Override
    public void enterPrimaryExpression(CParser.PrimaryExpressionContext ctx) {
        // save variables to later check for error "not defined variable"

        // find line number and column number
        int line_num = ctx.getStart().getLine();
        int col_num = get_column(ctx);

        // check if name could be the name of a variable using regex
        String name = ctx.getText();
        String pattern = "[_a-zA-Z]\\w*";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(name);
        if (matcher.matches()) {
            // add variable to variable stack: keep track of variables in program
            Variables v = variables_stack.peek();
            Var x = new Var(name, line_num, col_num);
            v.vars.add(x);
        }
    }

    @Override
    public void exitPrimaryExpression(CParser.PrimaryExpressionContext ctx) {

    }

    @Override
    public void enterBlockItemList(CParser.BlockItemListContext ctx) {

    }

    @Override
    public void exitBlockItemList(CParser.BlockItemListContext ctx) {

    }


    @Override
    public void enterIterationStatement(CParser.IterationStatementContext ctx) {
        SymbolTable iteration = new SymbolTable("iteration", ctx.getStart().getLine());
        SymbolTable parent = stack.peek();
        parent.children.add(iteration);
        stack.push(iteration);

        Variables variables = new Variables();
        Variables parent_variables = variables_stack.peek();
        parent_variables.children.add(variables);
        variables_stack.push(variables);
    }

    @Override
    public void exitIterationStatement(CParser.IterationStatementContext ctx) {
        // Error 2: not defined error
        SymbolTable s = stack.pop();
        ArrayList<Var> vars = variables_stack.pop().vars;
        check_for_not_defined(vars, defined_in_all_scopes(s));
    }

    @Override
    public void enterSelectionStatement(CParser.SelectionStatementContext ctx) {
        SymbolTable selection = new SymbolTable("selection", ctx.getStart().getLine());
        SymbolTable parent = stack.peek();
        parent.children.add(selection);
        stack.push(selection);

        Variables variables = new Variables();
        Variables parent_variables = variables_stack.peek();
        parent_variables.children.add(variables);
        variables_stack.push(variables);
    }

    @Override
    public void exitSelectionStatement(CParser.SelectionStatementContext ctx) {
        // Error 2: not defined error
        SymbolTable s = stack.pop();
        ArrayList<Var> vars = variables_stack.pop().vars;
        check_for_not_defined(vars, defined_in_all_scopes(s));
    }

    @Override
    public void enterExternalDeclaration(CParser.ExternalDeclarationContext ctx) {
        // store details of functions listed in program

        program = new SymbolTable("program", ctx.getStart().getLine());
        stack.push(program);
        Hashtable<String, String> table = new Hashtable<>();
        program.table = table;

        variables_root = new Variables();
        variables_stack.push(variables_root);

        String function_name;
        String function_type;
        for (int i = 0; i < ctx.functionDefinition().size(); i++) {
            StringBuffer key = new StringBuffer();
            StringBuffer value = new StringBuffer();

            function_name = ctx.functionDefinition().get(i).declarator().directDeclarator().directDeclarator().getText();

            // Error 1: duplicate function
            boolean duplicate = check_if_duplicate(function_name, program.table);
            if (duplicate) {
                // print error
                StringBuffer error = new StringBuffer();
                int line_num = ctx.functionDefinition().get(i).getStart().getLine();
                String full_str = ctx.getText();
                int column = full_str.indexOf(function_name);
                error.append("Error104 : in line " + line_num + ":" + column);
                error.append(" , method " + function_name + " has been defined already");
                System.out.println(error);

                // add tag to function name that is duplicated
                function_name += "_" + line_num + "_" + column;
            }

            function_type = ctx.functionDefinition().get(i).typeSpecifier().getText();

            key.append("Method_" + function_name); // for example Method_main
            value.append("Method (name : " + function_name + ") ");
            value.append("(return type: " + function_type + ")");

            // parameters
            StringBuffer paramsBuffer = new StringBuffer();
            if (ctx.functionDefinition().get(i).declarator().directDeclarator().parameterTypeList() != null) {
                List<CParser.ParameterDeclarationContext> p = ctx.functionDefinition().get(i).declarator().directDeclarator().parameterTypeList().parameterList().parameterDeclaration(); // list of param
                for (int j = 0; j < p.size(); j++) {
                    paramsBuffer.append(p.get(j).declarationSpecifiers().getText()); // param type
                    String param_name = p.get(j).declarator().directDeclarator().getText();
                    if (param_name.contains("[")) { // array
                        paramsBuffer.append(" array");
                    }
                    if (j < p.size() - 1) paramsBuffer.append(", ");
                }
            }
            value.append(" (parameter list: ");
            String[] params = paramsBuffer.toString().replaceAll("\\s+", " ").split(",");
            for (int k = 0; k < params.length; k++) {
                value.append("[type: " + params[k] + ", index: " + k + "] ");
            }

            // store in table
            table.put(key.toString(), value.toString());
        }
    }

    @Override
    public void exitExternalDeclaration(CParser.ExternalDeclarationContext ctx) {
        stack.pop();
        variables_stack.pop();
        // print symbol tables (phase 2 of project)
        SymbolTable.print_all_tables(program);
    }

    @Override
    public void enterFunctionDefinition(CParser.FunctionDefinitionContext ctx) {
        // store details of functions body in new symbol table: parameters of function

        // related to error 3
        this.return_type = ctx.typeSpecifier().getText();

        String function_name = ctx.declarator().directDeclarator().directDeclarator().getText();
        SymbolTable method = new SymbolTable(function_name, ctx.getStart().getLine());
        stack.push(method);
        program.children.add(method);
        Hashtable<String, String> table = new Hashtable<>();
        method.table = table;

        Variables variables = new Variables();
        variables_stack.push(variables);
        variables_root.children.add(variables);

        StringBuffer key;
        StringBuffer value;
        if (ctx.declarator().directDeclarator().parameterTypeList() != null) { // it has parameter fields
            List<gen.CParser.ParameterDeclarationContext> p = ctx.declarator().directDeclarator().parameterTypeList().parameterList().parameterDeclaration(); // list of params
            for (int i = 0; i < p.size(); i++) {
                // initialize key and value buffers
                key = new StringBuffer();
                value = new StringBuffer();

                // retrieve name and type of param
                String param_name = p.get(i).declarator().directDeclarator().getText();
                String param_type = p.get(i).declarationSpecifiers().getText();
                if (param_name.contains("[")) { // array
                    param_name = param_name.substring(0, param_name.indexOf('['));
                    param_type += " array";
                }

                // set key
                key.append("Field_" + param_name);

                // set value
                value.append("MethodParamField (name: " + param_name + ") ");
                value.append("(type: " + param_type + ")");

                // update table
                table.put(key.toString(), value.toString());
            }
        }
    }

    @Override
    public void exitFunctionDefinition(CParser.FunctionDefinitionContext ctx) {
        // Error 2: not defined error
        SymbolTable s = stack.pop();
        ArrayList<Var> vars = variables_stack.pop().vars;
        check_for_not_defined(vars, defined_in_all_scopes(s));
    }

    String variable_datatype(String name) {
        Hashtable<String, String> defined = defined_in_all_scopes(stack.peek());
        String type = null;
        for (Map.Entry<String, String> entry : defined.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.equals(name))
                type = value;
        }
        return type;
    }

    @Override
    public void enterJumpStatement(CParser.JumpStatementContext ctx) {
        String result;
        String variable_name = null;
        if (ctx.expression() != null) {
            variable_name = ctx.expression().getText();
            String variable_type = variable_datatype(variable_name);
            result = variable_type;
        } else {
            result = "void";
        }

        // check if return type checks out
        if (this.return_type.equals(result)) {
            this.return_type_match = true;
        }

        // Error 3: return type doesn't match
        if (!this.return_type_match) {
            StringBuffer buffer = new StringBuffer("Error210 : in line ");
            int line_num = ctx.getStart().getLine();
            int col_num = 0;
            if (ctx.expression() != null) {
                col_num = ctx.getText().indexOf(variable_name);
            }
            buffer.append(line_num + ":" + col_num);
            buffer.append(", ReturnType of this method must be " + this.return_type);
            System.out.println(buffer);
        }
    }

    @Override
    public void exitJumpStatement(CParser.JumpStatementContext ctx) {

    }

    @Override
    public void enterStatement(CParser.StatementContext ctx) {

    }

    @Override
    public void exitStatement(CParser.StatementContext ctx) {

    }

    public int params_num_in_definition(String x) {
        int count;
        for (Map.Entry<String, String> entry : program.table.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            String name = key.substring(key.indexOf("_") + 1);

            if (x.equals(name)) {
                String params = value.substring(value.indexOf("["));
                // count number of parameters
                char target = '[';
                count = 0;
                for (int i = 0; i < params.length(); i++) {
                    if (params.charAt(i) == target) {
                        count++;
                    }
                }
                return count;
            }
        }
        return -1;
    }

    @Override
    public void enterPostfixExpression(CParser.PostfixExpressionContext ctx) {
        int params_num = 0;
        String name;
        if (ctx.argumentExpressionList() != null && ctx.argumentExpressionList().size() > 0) {
            String params = ctx.argumentExpressionList().get(0).getText();
            params_num = params.split(",").length;

            // find out how many parameters are in definition
            name = ctx.getText().substring(0, ctx.getText().indexOf("("));
            int defined_params_num = params_num_in_definition(name);

            if (params_num != defined_params_num && defined_params_num != -1) {
                int line_num = ctx.getStart().getLine();
                int col_num = get_column(ctx);
                StringBuffer buffer = new StringBuffer();
                buffer.append("Error220: in line " + line_num + ":" + col_num + ", Mismatch arguments.");
                System.out.println(buffer);
            }
        }
    }

    @Override
    public void exitPostfixExpression(CParser.PostfixExpressionContext ctx) {

    }

    @Override
    public void enterArgumentExpressionList(CParser.ArgumentExpressionListContext ctx) {

    }

    @Override
    public void exitArgumentExpressionList(CParser.ArgumentExpressionListContext ctx) {

    }

    @Override
    public void enterUnaryExpression(CParser.UnaryExpressionContext ctx) {

    }

    @Override
    public void exitUnaryExpression(CParser.UnaryExpressionContext ctx) {

    }

    @Override
    public void enterUnaryOperator(CParser.UnaryOperatorContext ctx) {

    }

    @Override
    public void exitUnaryOperator(CParser.UnaryOperatorContext ctx) {

    }

    @Override
    public void enterCastExpression(CParser.CastExpressionContext ctx) {

    }

    @Override
    public void exitCastExpression(CParser.CastExpressionContext ctx) {

    }

    @Override
    public void enterMultiplicativeExpression(CParser.MultiplicativeExpressionContext ctx) {

    }

    @Override
    public void exitMultiplicativeExpression(CParser.MultiplicativeExpressionContext ctx) {

    }

    @Override
    public void enterAdditiveExpression(CParser.AdditiveExpressionContext ctx) {

    }

    @Override
    public void exitAdditiveExpression(CParser.AdditiveExpressionContext ctx) {

    }

    @Override
    public void enterShiftExpression(CParser.ShiftExpressionContext ctx) {

    }

    @Override
    public void exitShiftExpression(CParser.ShiftExpressionContext ctx) {

    }

    @Override
    public void enterRelationalExpression(CParser.RelationalExpressionContext ctx) {

    }

    @Override
    public void exitRelationalExpression(CParser.RelationalExpressionContext ctx) {

    }

    @Override
    public void enterEqualityExpression(CParser.EqualityExpressionContext ctx) {

    }

    @Override
    public void exitEqualityExpression(CParser.EqualityExpressionContext ctx) {

    }

    @Override
    public void enterAndExpression(CParser.AndExpressionContext ctx) {

    }

    @Override
    public void exitAndExpression(CParser.AndExpressionContext ctx) {

    }

    @Override
    public void enterExclusiveOrExpression(CParser.ExclusiveOrExpressionContext ctx) {

    }

    @Override
    public void exitExclusiveOrExpression(CParser.ExclusiveOrExpressionContext ctx) {

    }

    @Override
    public void enterInclusiveOrExpression(CParser.InclusiveOrExpressionContext ctx) {

    }

    @Override
    public void exitInclusiveOrExpression(CParser.InclusiveOrExpressionContext ctx) {

    }

    @Override
    public void enterLogicalAndExpression(CParser.LogicalAndExpressionContext ctx) {

    }

    @Override
    public void exitLogicalAndExpression(CParser.LogicalAndExpressionContext ctx) {

    }

    @Override
    public void enterLogicalOrExpression(CParser.LogicalOrExpressionContext ctx) {

    }

    @Override
    public void exitLogicalOrExpression(CParser.LogicalOrExpressionContext ctx) {

    }

    @Override
    public void enterConditionalExpression(CParser.ConditionalExpressionContext ctx) {

    }

    @Override
    public void exitConditionalExpression(CParser.ConditionalExpressionContext ctx) {

    }

    @Override
    public void enterAssignmentExpression(CParser.AssignmentExpressionContext ctx) {

    }

    @Override
    public void exitAssignmentExpression(CParser.AssignmentExpressionContext ctx) {

    }

    @Override
    public void enterAssignmentOperator(CParser.AssignmentOperatorContext ctx) {

    }

    @Override
    public void exitAssignmentOperator(CParser.AssignmentOperatorContext ctx) {

    }

    @Override
    public void enterExpression(CParser.ExpressionContext ctx) {

    }

    @Override
    public void exitExpression(CParser.ExpressionContext ctx) {

    }

    @Override
    public void enterConstantExpression(CParser.ConstantExpressionContext ctx) {

    }

    @Override
    public void exitConstantExpression(CParser.ConstantExpressionContext ctx) {

    }

    @Override
    public void enterDeclaration(CParser.DeclarationContext ctx) {
        // store details of field definitions in related symbol table
        String name;
        String type;
        StringBuffer buffer = new StringBuffer();

        StringBuffer key;
        StringBuffer value;


        // initialize buffers
        key = new StringBuffer();
        value = new StringBuffer();

        // get type
        type = ctx.declarationSpecifiers().declarationSpecifier().get(0).getText();

        // get name of each field
        if (ctx.initDeclaratorList() != null) {  // array or assignment
            for (int i = 0; i < ctx.initDeclaratorList().initDeclarator().size(); i++) {

                key = new StringBuffer();
                value = new StringBuffer();
                buffer = new StringBuffer();

                name = ctx.initDeclaratorList().initDeclarator().get(i).declarator().getText();

                String length = null;
                // check if it's an array
                if (name.contains("[")) { // array
                    String temp = name.substring(0, name.indexOf('['));
                    length = name.substring(name.indexOf('[') + 1, name.indexOf(']'));
                    name = temp;
                }

                // Error 1: duplicate field definition
                boolean duplicate = check_if_duplicate(name, stack.peek().table);
                if (duplicate) {
                    // print error
                    StringBuffer error = new StringBuffer();
                    int line_num = ctx.getStart().getLine();
                    int column = ctx.getText().indexOf(name);
                    error.append("Error104 : in line " + line_num + ":" + column);
                    error.append(" , field " + name + " has been defined already");
                    System.out.println(error);

                    // add tag to field name that is duplicated
                    name += "_" + line_num + "_" + column;
                }

                // set key
                key.append("Field_" + name);

                // set value
                value.append("methodField (name: " + name + ") ");
                if (length == null) { // non array
                    value.append("(type: " + type + ")");
                } else {
                    value.append("(type: " + type + " array, length=" + length + ")");
                }

                // add to the symbol table on top of stack
                SymbolTable st = stack.peek();
                st.table.put(key.toString(), value.toString());
            }
        } else {
            // single variable definition
            name = ctx.declarationSpecifiers().declarationSpecifier().get(1).getText();
            // set key
            key.append("Field_" + name);
            // set value
            value.append("methodField (name: " + name + ") ");
            value.append("(type: " + type + ")");

            // add to the symbol table on top of stack
            SymbolTable st = stack.peek();
            st.table.put(key.toString(), value.toString());
        }
    }

    @Override
    public void exitDeclaration(CParser.DeclarationContext ctx) {

    }

    @Override
    public void enterDeclarationSpecifiers(CParser.DeclarationSpecifiersContext ctx) {

    }

    @Override
    public void exitDeclarationSpecifiers(CParser.DeclarationSpecifiersContext ctx) {

    }

    @Override
    public void enterDeclarationSpecifier(CParser.DeclarationSpecifierContext ctx) {

    }

    @Override
    public void exitDeclarationSpecifier(CParser.DeclarationSpecifierContext ctx) {

    }

    @Override
    public void enterInitDeclaratorList(CParser.InitDeclaratorListContext ctx) {

    }

    @Override
    public void exitInitDeclaratorList(CParser.InitDeclaratorListContext ctx) {

    }

    @Override
    public void enterInitDeclarator(CParser.InitDeclaratorContext ctx) {

    }

    @Override
    public void exitInitDeclarator(CParser.InitDeclaratorContext ctx) {

    }

    @Override
    public void enterStorageClassSpecifier(CParser.StorageClassSpecifierContext ctx) {

    }

    @Override
    public void exitStorageClassSpecifier(CParser.StorageClassSpecifierContext ctx) {

    }

    @Override
    public void enterTypeSpecifier(CParser.TypeSpecifierContext ctx) {

    }

    @Override
    public void exitTypeSpecifier(CParser.TypeSpecifierContext ctx) {

    }

    @Override
    public void enterStructOrUnionSpecifier(CParser.StructOrUnionSpecifierContext ctx) {

    }

    @Override
    public void exitStructOrUnionSpecifier(CParser.StructOrUnionSpecifierContext ctx) {

    }

    @Override
    public void enterStructOrUnion(CParser.StructOrUnionContext ctx) {

    }

    @Override
    public void exitStructOrUnion(CParser.StructOrUnionContext ctx) {

    }

    @Override
    public void enterStructDeclarationList(CParser.StructDeclarationListContext ctx) {

    }

    @Override
    public void exitStructDeclarationList(CParser.StructDeclarationListContext ctx) {

    }

    @Override
    public void enterStructDeclaration(CParser.StructDeclarationContext ctx) {

    }

    @Override
    public void exitStructDeclaration(CParser.StructDeclarationContext ctx) {

    }

    @Override
    public void enterSpecifierQualifierList(CParser.SpecifierQualifierListContext ctx) {

    }

    @Override
    public void exitSpecifierQualifierList(CParser.SpecifierQualifierListContext ctx) {

    }

    @Override
    public void enterStructDeclaratorList(CParser.StructDeclaratorListContext ctx) {

    }

    @Override
    public void exitStructDeclaratorList(CParser.StructDeclaratorListContext ctx) {

    }

    @Override
    public void enterStructDeclarator(CParser.StructDeclaratorContext ctx) {

    }

    @Override
    public void exitStructDeclarator(CParser.StructDeclaratorContext ctx) {

    }

    @Override
    public void enterEnumSpecifier(CParser.EnumSpecifierContext ctx) {

    }

    @Override
    public void exitEnumSpecifier(CParser.EnumSpecifierContext ctx) {

    }

    @Override
    public void enterEnumeratorList(CParser.EnumeratorListContext ctx) {

    }

    @Override
    public void exitEnumeratorList(CParser.EnumeratorListContext ctx) {

    }

    @Override
    public void enterEnumerator(CParser.EnumeratorContext ctx) {

    }

    @Override
    public void exitEnumerator(CParser.EnumeratorContext ctx) {

    }

    @Override
    public void enterEnumerationConstant(CParser.EnumerationConstantContext ctx) {

    }

    @Override
    public void exitEnumerationConstant(CParser.EnumerationConstantContext ctx) {

    }

    @Override
    public void enterTypeQualifier(CParser.TypeQualifierContext ctx) {

    }

    @Override
    public void exitTypeQualifier(CParser.TypeQualifierContext ctx) {

    }

    @Override
    public void enterDeclarator(CParser.DeclaratorContext ctx) {

    }

    @Override
    public void exitDeclarator(CParser.DeclaratorContext ctx) {

    }

    @Override
    public void enterDirectDeclarator(CParser.DirectDeclaratorContext ctx) {

    }

    @Override
    public void exitDirectDeclarator(CParser.DirectDeclaratorContext ctx) {

    }

    @Override
    public void enterNestedParenthesesBlock(CParser.NestedParenthesesBlockContext ctx) {

    }

    @Override
    public void exitNestedParenthesesBlock(CParser.NestedParenthesesBlockContext ctx) {

    }

    @Override
    public void enterPointer(CParser.PointerContext ctx) {

    }

    @Override
    public void exitPointer(CParser.PointerContext ctx) {

    }

    @Override
    public void enterTypeQualifierList(CParser.TypeQualifierListContext ctx) {

    }

    @Override
    public void exitTypeQualifierList(CParser.TypeQualifierListContext ctx) {

    }

    @Override
    public void enterParameterTypeList(CParser.ParameterTypeListContext ctx) {

    }

    @Override
    public void exitParameterTypeList(CParser.ParameterTypeListContext ctx) {

    }

    @Override
    public void enterParameterList(CParser.ParameterListContext ctx) {

    }

    @Override
    public void exitParameterList(CParser.ParameterListContext ctx) {

    }

    @Override
    public void enterParameterDeclaration(CParser.ParameterDeclarationContext ctx) {

    }

    @Override
    public void exitParameterDeclaration(CParser.ParameterDeclarationContext ctx) {

    }

    @Override
    public void enterIdentifierList(CParser.IdentifierListContext ctx) {
    }

    @Override
    public void exitIdentifierList(CParser.IdentifierListContext ctx) {

    }

    @Override
    public void enterTypeName(CParser.TypeNameContext ctx) {

    }

    @Override
    public void exitTypeName(CParser.TypeNameContext ctx) {

    }

    @Override
    public void enterTypedefName(CParser.TypedefNameContext ctx) {

    }

    @Override
    public void exitTypedefName(CParser.TypedefNameContext ctx) {

    }

    @Override
    public void enterInitializer(CParser.InitializerContext ctx) {

    }

    @Override
    public void exitInitializer(CParser.InitializerContext ctx) {

    }

    @Override
    public void enterInitializerList(CParser.InitializerListContext ctx) {

    }

    @Override
    public void exitInitializerList(CParser.InitializerListContext ctx) {

    }

    @Override
    public void enterDesignation(CParser.DesignationContext ctx) {

    }

    @Override
    public void exitDesignation(CParser.DesignationContext ctx) {

    }

    @Override
    public void enterDesignatorList(CParser.DesignatorListContext ctx) {

    }

    @Override
    public void exitDesignatorList(CParser.DesignatorListContext ctx) {

    }

    @Override
    public void enterDesignator(CParser.DesignatorContext ctx) {

    }

    @Override
    public void exitDesignator(CParser.DesignatorContext ctx) {

    }

    @Override
    public void enterLabeledStatement(CParser.LabeledStatementContext ctx) {

    }

    @Override
    public void exitLabeledStatement(CParser.LabeledStatementContext ctx) {

    }

    @Override
    public void enterCompoundStatement(CParser.CompoundStatementContext ctx) {

    }

    @Override
    public void exitCompoundStatement(CParser.CompoundStatementContext ctx) {

    }

    @Override
    public void enterBlockItem(CParser.BlockItemContext ctx) {

    }

    @Override
    public void exitBlockItem(CParser.BlockItemContext ctx) {

    }

    @Override
    public void enterExpressionStatement(CParser.ExpressionStatementContext ctx) {

    }

    @Override
    public void exitExpressionStatement(CParser.ExpressionStatementContext ctx) {

    }

    @Override
    public void enterForCondition(CParser.ForConditionContext ctx) {

    }

    @Override
    public void exitForCondition(CParser.ForConditionContext ctx) {

    }

    @Override
    public void enterForDeclaration(CParser.ForDeclarationContext ctx) {
        // store details of field definitions in for loops in related symbol table

        String name;
        String type;
        StringBuffer buffer = new StringBuffer();

        StringBuffer key;
        StringBuffer value;

        // initialize buffers
        key = new StringBuffer();
        value = new StringBuffer();

        // get type
        type = ctx.declarationSpecifiers().declarationSpecifier().get(0).getText();

        // get name of each field
        if (ctx.initDeclaratorList() != null) {  // array or assignment
            for (int i = 0; i < ctx.initDeclaratorList().initDeclarator().size(); i++) {

                key = new StringBuffer();
                value = new StringBuffer();
                buffer = new StringBuffer();

                name = ctx.initDeclaratorList().initDeclarator().get(i).declarator().getText();

                String length = null;
                // check if it's an array
                if (name.contains("[")) { // array
                    String temp = name.substring(0, name.indexOf('['));
                    length = name.substring(name.indexOf('[') + 1, name.indexOf(']'));
                    name = temp;
                }

                // Error 1: duplicate field definition
                boolean duplicate = check_if_duplicate(name, stack.peek().table);
                if (duplicate) {
                    // print error
                    StringBuffer error = new StringBuffer();
                    int line_num = ctx.getStart().getLine();
                    int column = ctx.getText().indexOf(name);
                    error.append("Error104 : in line " + line_num + ":" + column);
                    error.append(" , field " + name + " has been defined already");
                    System.out.println(error);

                    // add tag to field name that is duplicated
                    name += "_" + line_num + "_" + column;
                }

                // set key
                key.append("Field_" + name);

                // set value
                value.append("methodField (name: " + name + ") ");
                if (length == null) { // non array
                    value.append("(type: " + type + ")");
                } else {
                    value.append("(type: " + type + " array, length=" + length + ")");
                }

                // add to the symbol table on top of stack
                SymbolTable st = stack.peek();
                st.table.put(key.toString(), value.toString());
            }
        }
    }

    @Override
    public void exitForDeclaration(CParser.ForDeclarationContext ctx) {

    }

    @Override
    public void enterForExpression(CParser.ForExpressionContext ctx) {

    }

    @Override
    public void exitForExpression(CParser.ForExpressionContext ctx) {

    }

    @Override
    public void enterDeclarationList(CParser.DeclarationListContext ctx) {

    }

    @Override
    public void exitDeclarationList(CParser.DeclarationListContext ctx) {

    }

    @Override
    public void visitTerminal(TerminalNode terminalNode) {

    }

    @Override
    public void visitErrorNode(ErrorNode errorNode) {

    }

    @Override
    public void enterEveryRule(ParserRuleContext parserRuleContext) {

    }

    @Override
    public void exitEveryRule(ParserRuleContext parserRuleContext) {

    }
}
