
package org.example.sourcecodeanalyser.extractor;

import org.example.sourcecodeanalyser.model.SCSSAnalysisResult;
import org.example.sourcecodeanalyser.model.SCSSRuleInfo;
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
public class SCSSSourceExtractor {

    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile(
                    "(?m)^\\s*(\\$[\\w-]+)\\s*:\\s*(.+?)\\s*;\\s*$"
            );

    private static final Pattern MIXIN_PATTERN =
            Pattern.compile("@mixin\\s+([\\w-]+)\\s*(\\([^{}]*\\))?");

    private static final Pattern INCLUDE_PATTERN =
            Pattern.compile("@include\\s+([\\w-]+)");

    private static final Pattern FUNCTION_PATTERN =
            Pattern.compile("@function\\s+([\\w-]+)\\s*(\\([^{}]*\\))?");

    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("@import\\s+([^;]+);");

    private static final Pattern USE_PATTERN =
            Pattern.compile("@use\\s+([^;]+);");

    private static final Pattern FORWARD_PATTERN =
            Pattern.compile("@forward\\s+([^;]+);");

    private static final Pattern EXTEND_PATTERN =
            Pattern.compile("@extend\\s+([^;]+);");

    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("%[\\w-]+");

    public SCSSAnalysisResult extract(Path scssFile)
            throws IOException {

        String source = Files.readString(
                scssFile,
                StandardCharsets.UTF_8
        );

        String cleanSource = removeComments(source);

        List<SCSSRuleInfo> rules = new ArrayList<>();

        parseBlocks(cleanSource, 0, cleanSource.length(), null, rules);

        Set<String> variables = extractMatches(
                VARIABLE_PATTERN, cleanSource, 1
        );

        Set<String> mixins = extractMatches(
                MIXIN_PATTERN, cleanSource, 0
        );

        Set<String> includes = extractMatches(
                INCLUDE_PATTERN, cleanSource, 0
        );

        Set<String> functions = extractMatches(
                FUNCTION_PATTERN, cleanSource, 0
        );

        Set<String> imports = extractMatches(
                IMPORT_PATTERN, cleanSource, 1
        );

        Set<String> uses = extractMatches(
                USE_PATTERN, cleanSource, 1
        );

        Set<String> forwards = extractMatches(
                FORWARD_PATTERN, cleanSource, 1
        );

        Set<String> extendsRules = extractMatches(
                EXTEND_PATTERN, cleanSource, 1
        );

        Set<String> placeholders = extractMatches(
                PLACEHOLDER_PATTERN, cleanSource, 0
        );

        return new SCSSAnalysisResult(
                scssFile.toString(),
                rules,
                new ArrayList<>(variables),
                new ArrayList<>(mixins),
                new ArrayList<>(includes),
                new ArrayList<>(functions),
                new ArrayList<>(imports),
                new ArrayList<>(uses),
                new ArrayList<>(forwards),
                new ArrayList<>(extendsRules),
                new ArrayList<>(placeholders)
        );
    }

    private void parseBlocks(
            String source,
            int start,
            int end,
            String parentSelector,
            List<SCSSRuleInfo> rules) {

        int cursor = start;

        while (cursor < end) {

            int openBrace = findNextOpenBrace(source, cursor, end);

            if (openBrace == -1) {
                break;
            }

            int headerStart = findHeaderStart(
                    source, cursor, openBrace
            );

            String header = source.substring(
                    headerStart, openBrace
            ).trim();

            int closeBrace = findMatchingBrace(
                    source, openBrace, end
            );

            if (closeBrace == -1) {
                break;
            }

            if (!header.isEmpty()) {

                if (header.startsWith("@")) {

                    // At-rules such as @media and @supports.
                    parseBlocks(
                            source,
                            openBrace + 1,
                            closeBrace,
                            parentSelector,
                            rules
                    );

                } else {

                    String selector = combineSelectors(
                            parentSelector,
                            header
                    );

                    List<String> declarations =
                            new ArrayList<>();

                    collectDeclarations(
                            source,
                            openBrace + 1,
                            closeBrace,
                            declarations
                    );

                    List<String> selectors = splitSelectors(selector);

                    for (String individualSelector : selectors) {

                        if (!individualSelector.isBlank()) {

                            rules.add(new SCSSRuleInfo(
                                    individualSelector.trim(),
                                    declarations,
                                    getLineNumber(source, headerStart),
                                    getLineNumber(source, closeBrace)
                            ));
                        }
                    }

                    parseBlocks(
                            source,
                            openBrace + 1,
                            closeBrace,
                            selector,
                            rules
                    );
                }
            }

            cursor = closeBrace + 1;
        }
    }

    private List<String> splitSelectors(String selector) {

        List<String> selectors = new ArrayList<>();

        StringBuilder current = new StringBuilder();

        int parentheses = 0;
        int brackets = 0;
        char quote = 0;

        for (int i = 0; i < selector.length(); i++) {

            char ch = selector.charAt(i);

            if (quote != 0) {
                current.append(ch);

                if (ch == quote && !isEscaped(selector, i)) {
                    quote = 0;
                }

                continue;
            }

            if (ch == '"' || ch == '\'') {
                quote = ch;
                current.append(ch);
            } else if (ch == '(') {
                parentheses++;
                current.append(ch);
            } else if (ch == ')') {
                parentheses--;
                current.append(ch);
            } else if (ch == '[') {
                brackets++;
                current.append(ch);
            } else if (ch == ']') {
                brackets--;
                current.append(ch);
            } else if (ch == ',' && parentheses == 0 && brackets == 0) {

                selectors.add(current.toString().trim());
                current.setLength(0);

            } else {
                current.append(ch);
            }
        }

        if (!current.toString().isBlank()) {
            selectors.add(current.toString().trim());
        }

        return selectors;
    }

    private void collectDeclarations(
            String source,
            int start,
            int end,
            List<String> declarations) {

        int segmentStart = start;
        int parentheses = 0;
        char quote = 0;

        for (int i = start; i < end; i++) {

            char current = source.charAt(i);

            if (quote != 0) {
                if (current == quote && !isEscaped(source, i)) {
                    quote = 0;
                }
                continue;
            }

            if (current == '"' || current == '\'') {
                quote = current;
                continue;
            }

            if (current == '(') {
                parentheses++;
            } else if (current == ')') {
                parentheses = Math.max(0, parentheses - 1);
            } else if (current == ';' && parentheses == 0) {

                addDeclaration(
                        source.substring(segmentStart, i),
                        declarations
                );

                segmentStart = i + 1;
            }
        }

        addDeclaration(
                source.substring(segmentStart, end),
                declarations
        );
    }

    private void addDeclaration(
            String text,
            List<String> declarations) {

        String declaration = text.trim();

        if (declaration.isEmpty()) {
            return;
        }

        int colonIndex = declaration.indexOf(':');

        if (colonIndex <= 0) {
            return;
        }

        String property = declaration.substring(
                0, colonIndex
        ).trim();

        String value = declaration.substring(
                colonIndex + 1
        ).trim();

        if (property.isEmpty() || value.isEmpty()) {
            return;
        }

        declarations.add(property + ": " + value);
    }

    private String combineSelectors(
            String parentSelector,
            String childSelector) {

        if (parentSelector == null || parentSelector.isBlank()) {
            return childSelector;
        }

        if (childSelector.contains("&")) {
            return childSelector.replace("&", parentSelector);
        }

        return parentSelector + " " + childSelector;
    }

    private int findNextOpenBrace(
            String source,
            int start,
            int end) {

        char quote = 0;
        int parentheses = 0;

        for (int i = start; i < end; i++) {

            char current = source.charAt(i);

            if (quote != 0) {
                if (current == quote && !isEscaped(source, i)) {
                    quote = 0;
                }
                continue;
            }

            if (current == '"' || current == '\'') {
                quote = current;
            } else if (current == '(') {
                parentheses++;
            } else if (current == ')') {
                parentheses = Math.max(0, parentheses - 1);
            } else if (current == '{' && parentheses == 0) {
                return i;
            }
        }

        return -1;
    }

    private int findMatchingBrace(
            String source,
            int openBrace,
            int end) {

        int depth = 0;
        char quote = 0;

        for (int i = openBrace; i < end; i++) {

            char current = source.charAt(i);

            if (quote != 0) {
                if (current == quote && !isEscaped(source, i)) {
                    quote = 0;
                }
                continue;
            }

            if (current == '"' || current == '\'') {
                quote = current;
            } else if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;

                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    private int findHeaderStart(
            String source,
            int start,
            int openBrace) {

        int headerStart = start;

        for (int i = start; i < openBrace; i++) {

            char current = source.charAt(i);

            if (current == ';' || current == '}') {
                headerStart = i + 1;
            }
        }

        return headerStart;
    }

    private int getLineNumber(String source, int position) {

        int lineNumber = 1;

        for (int i = 0; i < position; i++) {
            if (source.charAt(i) == '\n') {
                lineNumber++;
            }
        }

        return lineNumber;
    }

    private boolean isEscaped(String source, int position) {

        int backslashes = 0;

        for (int i = position - 1;
             i >= 0 && source.charAt(i) == '\\';
             i--) {

            backslashes++;
        }

        return backslashes % 2 != 0;
    }

    private Set<String> extractMatches(
            Pattern pattern,
            String source,
            int group) {

        Set<String> results = new LinkedHashSet<>();

        Matcher matcher = pattern.matcher(source);

        while (matcher.find()) {
            results.add(matcher.group(group).trim());
        }

        return results;
    }

    private String removeComments(String source) {

        StringBuilder result = new StringBuilder(source);

        boolean inBlockComment = false;
        boolean inLineComment = false;
        char quote = 0;

        for (int i = 0; i < result.length(); i++) {

            char current = result.charAt(i);
            char next = i + 1 < result.length()
                    ? result.charAt(i + 1)
                    : '\0';

            if (inBlockComment) {

                if (current == '*' && next == '/') {
                    result.setCharAt(i, ' ');
                    result.setCharAt(i + 1, ' ');
                    i++;
                    inBlockComment = false;
                } else if (current != '\n' && current != '\r') {
                    result.setCharAt(i, ' ');
                }

                continue;
            }

            if (inLineComment) {

                if (current == '\n' || current == '\r') {
                    inLineComment = false;
                } else {
                    result.setCharAt(i, ' ');
                }

                continue;
            }

            if (quote != 0) {

                if (current == quote && !isEscaped(result.toString(), i)) {
                    quote = 0;
                }

                continue;
            }

            if (current == '"' || current == '\'') {
                quote = current;
                continue;
            }

            if (current == '/' && next == '*') {
                result.setCharAt(i, ' ');
                result.setCharAt(i + 1, ' ');
                i++;
                inBlockComment = true;
            } else if (current == '/' && next == '/') {
                result.setCharAt(i, ' ');
                result.setCharAt(i + 1, ' ');
                i++;
                inLineComment = true;
            }
        }

        return result.toString();
    }
}
