package filter.ast.builder;

import filter.FilterParser;
import filter.ast.nodes.CompOp;
import filter.ast.nodes.Expr;
import filter.ast.nodes.Value;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class AstBuilderPattern {

    public Expr translate(FilterParser.QueryContext context) {
        return switch (context) {
            case FilterParser.QueryContext queryContext -> translateExpr(queryContext.expr());
        };
    }

    private Expr translateExpr(FilterParser.ExprContext context) {
        return switch (context) {
            case FilterParser.ExprContext exprContext -> translateOrExpr(exprContext.orExpr());
        };
    }

    private Expr translateOrExpr(FilterParser.OrExprContext context) {
        return switch (context) {
            case FilterParser.OrExprContext orExprContext ->
                foldLeft(orExprContext.andExpr(), this::translateAndExpr, Expr.Or::new);
        };
    }

    private Expr translateAndExpr(FilterParser.AndExprContext context) {
        return switch (context) {
            case FilterParser.AndExprContext andExprContext ->
                foldLeft(andExprContext.notExpr(), this::translateNotExpr, Expr.And::new);
        };
    }

    private Expr translateNotExpr(FilterParser.NotExprContext context) {
        return switch (context) {
            case FilterParser.NotExprContext notExprContext when notExprContext.NOT() != null ->
                new Expr.Not(translateNotExpr(notExprContext.notExpr()));
            case FilterParser.NotExprContext notExprContext -> translatePrimary(notExprContext.primary());
        };
    }

    private Expr translatePrimary(FilterParser.PrimaryContext context) {
        return switch (context) {
            case FilterParser.PrimaryContext primaryContext when primaryContext.comparison() != null ->
                translateComparison(primaryContext.comparison());
            case FilterParser.PrimaryContext primaryContext -> translateExpr(primaryContext.expr());
        };
    }

    private Expr translateComparison(FilterParser.ComparisonContext context) {
        return switch (context) {
            case FilterParser.ComparisonContext comparisonContext when comparisonContext.COMPOP() != null ->
                new Expr.Comparison(
                    comparisonContext.IDENTIFIER().getText(),
                    CompOp.fromSymbol(comparisonContext.op.getText()),
                    translateLiteral(comparisonContext.literal()));
            case FilterParser.ComparisonContext comparisonContext ->
                new Expr.InList(
                    comparisonContext.IDENTIFIER().getText(), translateLiteralList(comparisonContext.literalList()));
        };
    }

    private List<Value> translateLiteralList(FilterParser.LiteralListContext context) {
        return switch (context) {
            case FilterParser.LiteralListContext literalListContext ->
                literalListContext.literal().stream().map(this::translateLiteral).toList();
        };
    }

    private Value translateLiteral(FilterParser.LiteralContext context) {
        return switch (context) {
            case FilterParser.LiteralContext literalContext when literalContext.STRING() != null ->
                new Value.Str(stripQuotes(literalContext.STRING().getText()));
            case FilterParser.LiteralContext literalContext ->
                new Value.Num(Integer.parseInt(literalContext.NUMBER().getText()));
        };
    }

    private static <C> Expr foldLeft(
        List<C> contexts, Function<C, Expr> mapper, BiFunction<Expr, Expr, Expr> nodeFactory) {
        if (contexts.isEmpty()) {
            throw new IllegalArgumentException("Expected min. one parse-tree child");
        }

        Expr rootResult = mapper.apply(contexts.getFirst());
        for (int i = 1; i < contexts.size(); i++) {
            rootResult = nodeFactory.apply(rootResult, mapper.apply(contexts.get(i)));
        }
        return rootResult;
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
