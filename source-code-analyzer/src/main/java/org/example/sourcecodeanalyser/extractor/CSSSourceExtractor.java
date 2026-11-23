
package org.example.sourcecodeanalyser.extractor;

import com.helger.css.CSSSourceLocation;
import com.helger.css.decl.AbstractHasTopLevelRules;
import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSStyleRule;
import com.helger.css.decl.ICSSNestedRule;
import com.helger.css.decl.ICSSTopLevelRule;
import com.helger.css.decl.CascadingStyleSheet;
import com.helger.css.reader.CSSReader;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.writer.CSSWriterSettings;

import org.example.sourcecodeanalyser.model.CSSAnalysisResult;
import org.example.sourcecodeanalyser.model.CSSRuleInfo;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CSSSourceExtractor {

    private static final Pattern IMPORT_PATTERN =
            Pattern.compile(
                    "@import\\s+(?:url\\(\\s*)?['\"]?([^'\"\\s);]+)"
            );

    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile(
                    "(?:^|[;{])\\s*(--[\\w-]+)\\s*:\\s*([^;{}]+)"
            );

    public CSSAnalysisResult extract(Path cssFile) throws IOException {

        if (cssFile == null || !Files.isRegularFile(cssFile)) {
            throw new IOException(
                    "CSS file does not exist or is not a regular file: "
                            + cssFile
            );
        }

        // 1. Read CSS source.
        String source = Files.readString(
                cssFile,
                StandardCharsets.UTF_8
        );

        // 2. Parse CSS source.
        CascadingStyleSheet stylesheet =
                CSSReader.readFromStringReader(
                        source,
                        new CSSReaderSettings()
                );

        if (stylesheet == null) {
            throw new IOException(
                    "Unable to parse CSS file: " + cssFile
            );
        }

        List<CSSRuleInfo> rules = new ArrayList<>();

        Set<String> imports = new LinkedHashSet<>();
        Set<String> variables = new LinkedHashSet<>();

        CSSWriterSettings writerSettings =
                new CSSWriterSettings();

        // 3. Extract top-level and nested CSS rules.
        collectTopLevelRules(
                stylesheet.getAllRules(),
                rules,
                writerSettings
        );

        // 4. Remove comments before regex-based extraction.
        String sourceWithoutComments =
                source.replaceAll("(?s)/\\*.*?\\*/", "");

        // 5. Extract CSS imports.
        Matcher importMatcher =
                IMPORT_PATTERN.matcher(sourceWithoutComments);

        while (importMatcher.find()) {
            imports.add(importMatcher.group(1).trim());
        }

        // 6. Extract CSS custom properties.
        Matcher variableMatcher =
                VARIABLE_PATTERN.matcher(sourceWithoutComments);

        while (variableMatcher.find()) {

            variables.add(
                    variableMatcher.group(1)
                            + ": "
                            + variableMatcher.group(2).trim()
            );
        }

        // 7. Return extracted CSS information.
        return new CSSAnalysisResult(
                cssFile.toString(),
                rules.size(),
                rules,
                new ArrayList<>(imports),
                new ArrayList<>(variables)
        );
    }

    private void collectTopLevelRules(
            List<? extends ICSSTopLevelRule> sourceRules,
            List<CSSRuleInfo> extractedRules,
            CSSWriterSettings writerSettings) {

        for (ICSSTopLevelRule rule : sourceRules) {

            if (rule instanceof CSSStyleRule styleRule) {

                addStyleRule(
                        styleRule,
                        extractedRules,
                        writerSettings
                );

                collectNestedRules(
                        styleRule.getAllRules(),
                        extractedRules,
                        writerSettings
                );

            } else if (rule instanceof AbstractHasTopLevelRules container) {

                collectTopLevelRules(
                        container.getAllRules(),
                        extractedRules,
                        writerSettings
                );
            }
        }
    }

    private void collectNestedRules(
            List<? extends ICSSNestedRule> nestedRules,
            List<CSSRuleInfo> extractedRules,
            CSSWriterSettings writerSettings) {

        for (ICSSNestedRule nestedRule : nestedRules) {

            if (nestedRule instanceof CSSStyleRule styleRule) {

                addStyleRule(
                        styleRule,
                        extractedRules,
                        writerSettings
                );

                collectNestedRules(
                        styleRule.getAllRules(),
                        extractedRules,
                        writerSettings
                );

            } else if (nestedRule instanceof AbstractHasTopLevelRules container) {

                collectTopLevelRules(
                        container.getAllRules(),
                        extractedRules,
                        writerSettings
                );
            }
        }
    }

    private void addStyleRule(
            CSSStyleRule styleRule,
            List<CSSRuleInfo> extractedRules,
            CSSWriterSettings writerSettings) {

        List<String> declarations = new ArrayList<>();

        for (CSSDeclaration declaration : styleRule.getAllDeclarations()) {

            declarations.add(
                    declaration.getProperty()
                            + ": "
                            + declaration.getExpressionAsCSSString()
            );
        }

        int startLine = 0;
        int endLine = 0;

        CSSSourceLocation location =
                styleRule.getSourceLocation();

        if (location != null) {

            int firstLine =
                    location.getFirstTokenBeginLineNumber();

            int lastLine =
                    location.getLastTokenEndLineNumber();

            if (firstLine > 0) {
                startLine = firstLine;
            }

            if (lastLine > 0) {
                endLine = lastLine;
            }
        }

        extractedRules.add(
                new CSSRuleInfo(
                        styleRule.getSelectorsAsCSSString(
                                writerSettings,
                                0
                        ),
                        declarations,
                        startLine,
                        endLine
                )
        );
    }
}
