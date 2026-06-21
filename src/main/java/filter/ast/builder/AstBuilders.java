package filter.ast.builder;

import filter.FilterLexer;
import filter.FilterParser;
import filter.ast.nodes.Expr;

import java.util.List;
import java.util.function.Function;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public class AstBuilders {

  public static Expr fromQuery(String query, Function<FilterParser.QueryContext, Expr> translator) {
    return simplify(translator.apply(parse(query)));
  }

    public static Expr simplify(Expr e) {
        return switch (e) {
            case Expr.Not(Expr.Not(Expr inner)) -> simplify(inner);
            case Expr.Not(Expr inner) -> new Expr.Not(simplify(inner));
            case Expr.And(Expr left, Expr right) -> new Expr.And(simplify(left), simplify(right));
            case Expr.Or(Expr left, Expr right) -> new Expr.Or(simplify(left), simplify(right));
            case Expr.Comparison(String field, filter.ast.nodes.CompOp op, filter.ast.nodes.Value value) -> e;
            case Expr.InList(String field, List<filter.ast.nodes.Value> values) -> e;
        };
    }

  public static FilterParser.QueryContext parse(String query) {
    var cs = CharStreams.fromString(query);
    var lexer = new FilterLexer(cs);
    var tokens = new CommonTokenStream(lexer);
    var parser = new FilterParser(tokens);

    var ctx = parser.query();
    if (parser.getNumberOfSyntaxErrors() > 0)
      throw new IllegalStateException("Syntax errors in query: " + query);

    return ctx;
  }
}
