package filter.ast.builder;

import filter.FilterBaseVisitor;
import filter.FilterParser;
import filter.ast.nodes.CompOp;
import filter.ast.nodes.Expr;
import filter.ast.nodes.Value;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.BinaryOperator;

public class AstBuilderVisitor extends FilterBaseVisitor<Void> {

    private final Deque<Expr> expressions = new ArrayDeque<>();
    private final Deque<Value> values = new ArrayDeque<>();

    public Expr translate(FilterParser.QueryContext context) {
        expressions.clear();
        values.clear();

        visit(context);

        if (expressions.size() != 1 || !values.isEmpty()) {
            throw new IllegalStateException(
                "Invalid AST builder state: "
                    + expressions.size()
                    + " expression(s), "
                    + values.size()
                    + " value(s)");
        }

        return expressions.pop();
    }

    @Override
    public Void visitQuery(FilterParser.QueryContext context) {
        visit(context.expr());
        return null;
    }

    @Override
    public Void visitExpr(FilterParser.ExprContext context) {
        visit(context.orExpr());
        return null;
    }

    @Override
    public Void visitOrExpr(FilterParser.OrExprContext context) {
        context.andExpr().forEach(this::visit);
        expressions.push(foldPoppedExpressions(context.andExpr().size(), Expr.Or::new));
        return null;
    }

    @Override
    public Void visitAndExpr(FilterParser.AndExprContext context) {
        context.notExpr().forEach(this::visit);
        expressions.push(foldPoppedExpressions(context.notExpr().size(), Expr.And::new));
        return null;
    }

    @Override
    public Void visitNotExpr(FilterParser.NotExprContext context) {
        if (context.NOT() != null) {
            visit(context.notExpr());
            expressions.push(new Expr.Not(expressions.pop()));
        } else {
            visit(context.primary());
        }
        return null;
    }

    @Override
    public Void visitPrimary(FilterParser.PrimaryContext context) {
        if (context.comparison() != null) {
            visit(context.comparison());
        } else {
            visit(context.expr());
        }
        return null;
    }

    @Override
    public Void visitComparison(FilterParser.ComparisonContext context) {
        String fieldName = context.IDENTIFIER().getText();

        if (context.COMPOP() != null) {
            visit(context.literal());
            expressions.push(
                new Expr.Comparison(fieldName, CompOp.fromSymbol(context.op.getText()), values.pop()));
        } else {
            visit(context.literalList());
            expressions.push(new Expr.InList(fieldName, popValues(context.literalList().literal().size())));
        }

        return null;
    }

    @Override
    public Void visitLiteralList(FilterParser.LiteralListContext context) {
        context.literal().forEach(this::visit);
        return null;
    }

    @Override
    public Void visitLiteral(FilterParser.LiteralContext context) {
        if (context.STRING() != null) {
            values.push(new Value.Str(stripQuotes(context.STRING().getText())));
        } else {
            values.push(new Value.Num(Integer.parseInt(context.NUMBER().getText())));
        }
        return null;
    }

    private Expr foldPoppedExpressions(int count, BinaryOperator<Expr> nodeFactory) {
        if (count < 1) {
            throw new IllegalArgumentException("Expected at least one expression");
        }

        Deque<Expr> orderedExpressions = new ArrayDeque<>();
        for (int i = 0; i < count; i++) {
            orderedExpressions.addFirst(expressions.pop());
        }

        Expr rootResult = orderedExpressions.removeFirst();
        while (!orderedExpressions.isEmpty()) {
            rootResult = nodeFactory.apply(rootResult, orderedExpressions.removeFirst());
        }
        return rootResult;
    }

    private List<Value> popValues(int count) {
        Deque<Value> orderedValues = new ArrayDeque<>();
        for (int i = 0; i < count; i++) {
            orderedValues.addFirst(values.pop());
        }
        return List.copyOf(orderedValues);
    }

    private static String stripQuotes(String tokenText) {
        String contentWithoutQuotes = tokenText.substring(1, tokenText.length() - 1);
        StringBuilder unescapedContent = new StringBuilder(contentWithoutQuotes.length());

        for (int i = 0; i < contentWithoutQuotes.length(); i++) {
            char currentCharacter = contentWithoutQuotes.charAt(i);
            if (currentCharacter == '\\' && i + 1 < contentWithoutQuotes.length()) {
                unescapedContent.append(contentWithoutQuotes.charAt(++i));
            } else {
                unescapedContent.append(currentCharacter);
            }
        }

        return unescapedContent.toString();
    }
}
