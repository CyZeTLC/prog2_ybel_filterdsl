package filter.ast;

import filter.ast.builder.AstBuilderPattern;
import filter.ast.builder.AstBuilderVisitor;
import filter.ast.builder.AstBuilders;
import filter.ast.eval.Evaluator;
import filter.ast.nodes.CompOp;
import filter.ast.nodes.Expr;
import filter.ast.nodes.Value;
import filter.model.Genre;
import filter.model.MediaItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class AstTest {

    @Test
    void parseComplexQueryWithPrecedenceAndInListToValidAst() {
        String query = "genre in (\"power metal\", \"punk rock\") or year <= 1995 and not artist == \"Nirvana\"";

        Expr expectedAst =
            new Expr.Or(
                new Expr.InList("genre", List.of(new Value.Str("power metal"), new Value.Str("punk rock"))),
                new Expr.And(
                    new Expr.Comparison("year", CompOp.LE, new Value.Num(1995)),
                    new Expr.Not(new Expr.Comparison("artist", CompOp.EQ, new Value.Str("Nirvana")))));

        assertEquals(expectedAst, evaluatePatternBuilder(query));
    }

    @Test
    void parseChainedAndExpressionsWithLeftAssociativeOrder() {
        String query = "year > 1980 and artist == \"Blind Guardian\" and genre == \"power metal\"";

        Expr expectedAst =
            new Expr.And(
                new Expr.And(
                    new Expr.Comparison("year", CompOp.GT, new Value.Num(1980)),
                    new Expr.Comparison("artist", CompOp.EQ, new Value.Str("Blind Guardian"))),
                new Expr.Comparison("genre", CompOp.EQ, new Value.Str("power metal")));

        assertEquals(expectedAst, evaluateVisitorBuilder(query));
    }

    @Test
    void parseParenthesesToOverrideDefaultPrecedenceRules() {
        String query = "(year <= 1995 or artist == \"Die Toten Hosen\") and year > 1985";

        Expr expectedAst =
            new Expr.And(
                new Expr.Or(
                    new Expr.Comparison("year", CompOp.LE, new Value.Num(1995)),
                    new Expr.Comparison("artist", CompOp.EQ, new Value.Str("Die Toten Hosen"))),
                new Expr.Comparison("year", CompOp.GT, new Value.Num(1985)));

        assertAll(
            () -> assertEquals(expectedAst, evaluatePatternBuilder(query)),
            () -> assertEquals(expectedAst, evaluateVisitorBuilder(query))
        );
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "artist == \"Subway to Sally\"",
            "year == 1991",
            "not artist != \"Queen\"",
            "artist == \"Blind Guardian\" and year == 1992",
            "genre in (\"folk metal\", \"folk rock\")",
            "not (genre in (\"electronic\", \"rock\") or year >= 2010)"
        })
    void assertBothBuildersProduceIdenticalAstForStandardQueries(String query) {
        assertEquals(evaluatePatternBuilder(query), evaluateVisitorBuilder(query));
    }

    @Test
    void parseStringLiteralsWithInternalEscapedQuotesCorrectly() {
        Expr expectedAst = new Expr.Comparison("title", CompOp.EQ, new Value.Str("Bataillon d\"Amour"));

        assertAll(
            () -> assertEquals(expectedAst, evaluatePatternBuilder("title == \"Bataillon d\\\"Amour\"")),
            () -> assertEquals(expectedAst, evaluateVisitorBuilder("title == \"Bataillon d\\\"Amour\""))
        );
    }

    @Test
    void simplifyExpressionTreeByEliminatingDeepDoubleNegations() {
        Expr blindGuardianComparison = new Expr.Comparison("artist", CompOp.EQ, new Value.Str("Blind Guardian"));
        Expr metalComparison = new Expr.Comparison("genre", CompOp.EQ, new Value.Str("power metal"));

        Expr originalAst =
            new Expr.And(
                new Expr.Not(new Expr.Not(blindGuardianComparison)),
                new Expr.Not(new Expr.Not(new Expr.Not(metalComparison)))
            );

        Expr expectedAst = new Expr.And(blindGuardianComparison, new Expr.Not(metalComparison));

        assertEquals(expectedAst, AstBuilders.simplify(originalAst));
    }

    @Test
    void parseAndAutomaticallyNormalizeDoubleNegationsFromQueryString() {
        Expr expectedAst = new Expr.Comparison("artist", CompOp.EQ, new Value.Str("Die Toten Hosen"));

        Expr patternExpression =
            AstBuilders.fromQuery("not not artist == \"Die Toten Hosen\"", new AstBuilderPattern()::translate);
        Expr visitorExpression =
            AstBuilders.fromQuery("not not artist == \"Die Toten Hosen\"", new AstBuilderVisitor()::translate);

        assertAll(
            () -> assertEquals(expectedAst, patternExpression),
            () -> assertEquals(expectedAst, visitorExpression)
        );
    }

    @Test
    void evaluateMediaItemAgainstAstProducedByPatternBuilder() {
        String query = "artist == \"Subway to Sally\" and year == 1998";
        Expr expression = AstBuilders.fromQuery(query, new AstBuilderPattern()::translate);

        assertAll(
            () -> assertTrue(Evaluator.matches(new MediaItem("Veitstanz", "Subway to Sally", Genre.FOLK_METAL, 1998), expression)),
            () -> assertFalse(Evaluator.matches(new MediaItem("Mirror Mirror", "Blind Guardian", Genre.POWER_METAL, 1998), expression))
        );
    }

    private static Expr evaluatePatternBuilder(String query) {
        return new AstBuilderPattern().translate(AstBuilders.parse(query));
    }

    private static Expr evaluateVisitorBuilder(String query) {
        return new AstBuilderVisitor().translate(AstBuilders.parse(query));
    }
}
