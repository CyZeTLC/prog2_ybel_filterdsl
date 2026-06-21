package filter.ast;

import filter.ast.builder.AstBuilderPattern;
import filter.ast.builder.AstBuilderVisitor;
import filter.ast.builder.AstBuilders;
import filter.ast.nodes.Expr;
import filter.ast.printer.AstPrinter;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.StringJoiner;

public class ApprovalTest {

    private static final List<String> FILTER_QUERIES = List.of(
        "artist == \"Blind Guardian\"",
        "year == 1991",
        "artist == \"Die Toten Hosen\" and year == 1988",
        "year <= 1995 and artist == \"Nirvana\" and year > 1990",
        "(year <= 2000 or artist == \"Subway to Sally\") and year > 1995",
        "genre in (\"power metal\", \"punk rock\") or year <= 1990 and not artist == \"Queen\"",
        "not not artist == \"Dire Straits\"",
        "not (genre in (\"electronic\", \"rock\") or year >= 2000)"
    );

    private static final List<String> SIMPLIFICATION_QUERIES = List.of(
        "not not artist == \"Subway to Sally\"",
        "not not not artist == \"Blind Guardian\"",
        "not (not genre in (\"punk rock\", \"rock\") and not not year >= 1990)"
    );

    @Test
    void approvesBuilderOutputs() {
        Approvals.verify(generateCanonicalAstReport());
    }

    @Test
    void approvesSimplification() {
        StringJoiner report = new StringJoiner(System.lineSeparator() + System.lineSeparator());

        for (String query : SIMPLIFICATION_QUERIES) {
            Expr rawAst = new AstBuilderPattern().translate(AstBuilders.parse(query));
            Expr simplifiedAst = AstBuilders.simplify(rawAst);

            report.add(
                "Query: " + query + System.lineSeparator()
                    + "Raw: " + AstPrinter.toString(rawAst) + System.lineSeparator()
                    + "Simplified: " + AstPrinter.toString(simplifiedAst)
            );
        }

        Approvals.verify(report.toString());
    }

    private static String generateCanonicalAstReport() {
        StringJoiner report = new StringJoiner(System.lineSeparator() + System.lineSeparator());

        for (String query : FILTER_QUERIES) {
            Expr patternAst = AstBuilders.fromQuery(query, new AstBuilderPattern()::translate);
            Expr visitorAst = AstBuilders.fromQuery(query, new AstBuilderVisitor()::translate);

            report.add(
                "Query: " + query + System.lineSeparator()
                    + "Pattern: " + AstPrinter.toString(patternAst) + System.lineSeparator()
                    + "Visitor: " + AstPrinter.toString(visitorAst)
            );
        }

        return report.toString();
    }
}
