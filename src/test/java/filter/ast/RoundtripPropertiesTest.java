package filter.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;

import filter.ast.builder.AstBuilderPattern;
import filter.ast.builder.AstBuilderVisitor;
import filter.ast.builder.AstBuilders;
import filter.ast.eval.Evaluator;
import filter.ast.nodes.Expr;
import filter.ast.printer.AstPrinter;
import filter.model.Genre;
import filter.model.MediaItem;
import net.jqwik.api.*;

public class RoundtripPropertiesTest {

    @Property
    void patternBuilderRoundtripProducesStablePrettyPrint(@ForAll("simpleQueries") String query) {
        String firstIterationPrint = AstPrinter.toString(evaluatePatternBuilder(query));
        String secondIterationPrint = AstPrinter.toString(evaluatePatternBuilder(firstIterationPrint));

        assertEquals(firstIterationPrint, secondIterationPrint);
    }

    @Property
    void visitorBuilderRoundtripProducesStablePrettyPrint(@ForAll("simpleQueries") String query) {
        String firstIterationPrint = AstPrinter.toString(evaluateVisitorBuilder(query));
        String secondIterationPrint = AstPrinter.toString(evaluateVisitorBuilder(firstIterationPrint));

        assertEquals(firstIterationPrint, secondIterationPrint);
    }

    @Property
    void crossBuilderRoundtripFromVisitorToPatternRemainsStable(@ForAll("simpleQueries") String query) {
        String visitorGenerationPrint = AstPrinter.toString(evaluateVisitorBuilder(query));
        String patternRegenerationPrint = AstPrinter.toString(evaluatePatternBuilder(visitorGenerationPrint));

        assertEquals(visitorGenerationPrint, patternRegenerationPrint);
    }

    @Property
    void bothBuildersGenerateIdenticalAstPrettyPrints(@ForAll("simpleQueries") String query) {
        String patternPrint = AstPrinter.toString(evaluatePatternBuilder(query));
        String visitorPrint = AstPrinter.toString(evaluateVisitorBuilder(query));

        assertEquals(patternPrint, visitorPrint);
    }

    @Property
    void andEvaluationIsCommutativeForLogicalCombinations(
        @ForAll("atoms") String leftQuery,
        @ForAll("atoms") String rightQuery,
        @ForAll("mediaItems") MediaItem item) {
        Expr leftExpression = evaluatePatternBuilder(leftQuery);
        Expr rightExpression = evaluatePatternBuilder(rightQuery);

        boolean forwardEvaluationResult = Evaluator.matches(item, new Expr.And(leftExpression, rightExpression));
        boolean backwardEvaluationResult = Evaluator.matches(item, new Expr.And(rightExpression, leftExpression));

        assertEquals(forwardEvaluationResult, backwardEvaluationResult);
    }

    @Property
    void simplifyMaintainsSemanticIntegrityOfDoubleNegatedExpressions(
        @ForAll("simpleQueries") String query, @ForAll("mediaItems") MediaItem item) {
        Expr originalExpression = evaluatePatternBuilder(query);
        Expr doubleNegatedExpression = new Expr.Not(new Expr.Not(originalExpression));
        Expr simplifiedExpression = AstBuilders.simplify(doubleNegatedExpression);

        boolean originalEvaluationResult = Evaluator.matches(item, originalExpression);
        boolean simplifiedEvaluationResult = Evaluator.matches(item, simplifiedExpression);

        assertEquals(originalExpression, simplifiedExpression);
        assertEquals(originalEvaluationResult, simplifiedEvaluationResult);
    }

    @Provide
    Arbitrary<String> fields() {
        return Arbitraries.of("title", "artist", "genre", "year");
    }

    @Provide
    Arbitrary<String> values() {
        return Arbitraries.oneOf(stringLiterals(), numberLiterals());
    }

    @Provide
    Arbitrary<String> stringLiterals() {
        return Arbitraries.strings()
            .withChars("abcxyz")
            .ofMinLength(1)
            .ofMaxLength(5)
            .map(s -> "\"" + s + "\"");
    }

    @Provide
    Arbitrary<String> numberLiterals() {
        return Arbitraries.integers().between(1900, 2025).map(Object::toString);
    }

    @Provide
    Arbitrary<String> comparisons() {
        Arbitrary<String> operators = Arbitraries.of("==", "!=", "<", "<=", ">", ">=");

        Arbitrary<String> stringComparison =
            Combinators.combine(fields(), operators, stringLiterals())
                .as((field, op, literal) -> field + " " + op + " " + literal);

        Arbitrary<String> numberComparison =
            Combinators.combine(Arbitraries.of("year"), operators, numberLiterals())
                .as((field, op, literal) -> field + " " + op + " " + literal);

        return Arbitraries.oneOf(stringComparison, numberComparison);
    }

    @Provide
    Arbitrary<String> inLists() {
        return Combinators.combine(fields(), values().list().ofMinSize(1).ofMaxSize(4))
            .as((field, literals) -> field + " in (" + String.join(", ", literals) + ")");
    }

    @Provide
    Arbitrary<String> atoms() {
        return Arbitraries.oneOf(comparisons(), inLists());
    }

    @Provide
    Arbitrary<String> simpleQueries() {
        return comparisons()
            .list()
            .ofMinSize(1)
            .ofMaxSize(3)
            .map(
                list -> {
                    if (list.size() == 1) return list.getFirst();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) {
                            String conn = Arbitraries.of(" and ", " or ").sample();
                            sb.append(conn);
                        }
                        sb.append(list.get(i));
                    }
                    return sb.toString();
                });
    }

    @Provide
    Arbitrary<MediaItem> mediaItems() {
        return Combinators.combine(
                plainTexts(),
                plainTexts(),
                Arbitraries.of(Genre.values()),
                Arbitraries.integers().between(1900, 2025))
            .as(MediaItem::new);
    }

    @Provide
    Arbitrary<String> plainTexts() {
        return Arbitraries.strings()
            .withChars("abcxyz ")
            .ofMinLength(1)
            .ofMaxLength(12)
            .filter(text -> !text.isBlank());
    }

    private static Expr evaluatePatternBuilder(String query) {
        return AstBuilders.fromQuery(query, new AstBuilderPattern()::translate);
    }

    private static Expr evaluateVisitorBuilder(String query) {
        return AstBuilders.fromQuery(query, new AstBuilderVisitor()::translate);
    }
}
