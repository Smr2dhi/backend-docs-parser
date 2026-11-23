
package org.example.sourcecodeanalyser.model;

import java.util.List;

public class SCSSAnalysisResult {

    private String filePath;

    private int totalRules;
    private List<SCSSRuleInfo> rules;

    private int totalVariables;
    private List<String> variables;

    private List<String> mixins;
    private List<String> includes;
    private List<String> functions;
    private List<String> imports;
    private List<String> uses;
    private List<String> forwards;
    private List<String> extendsRules;
    private List<String> placeholders;

    public SCSSAnalysisResult(
            String filePath,
            List<SCSSRuleInfo> rules,
            List<String> variables,
            List<String> mixins,
            List<String> includes,
            List<String> functions,
            List<String> imports,
            List<String> uses,
            List<String> forwards,
            List<String> extendsRules,
            List<String> placeholders) {

        this.filePath = filePath;

        this.rules = rules;
        this.totalRules = rules.size();

        this.variables = variables;
        this.totalVariables = variables.size();

        this.mixins = mixins;
        this.includes = includes;
        this.functions = functions;
        this.imports = imports;
        this.uses = uses;
        this.forwards = forwards;
        this.extendsRules = extendsRules;
        this.placeholders = placeholders;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getTotalRules() {
        return totalRules;
    }

    public List<SCSSRuleInfo> getRules() {
        return rules;
    }

    public int getTotalVariables() {
        return totalVariables;
    }

    public List<String> getVariables() {
        return variables;
    }

    public List<String> getMixins() {
        return mixins;
    }

    public List<String> getIncludes() {
        return includes;
    }

    public List<String> getFunctions() {
        return functions;
    }

    public List<String> getImports() {
        return imports;
    }

    public List<String> getUses() {
        return uses;
    }

    public List<String> getForwards() {
        return forwards;
    }

    public List<String> getExtendsRules() {
        return extendsRules;
    }

    public List<String> getPlaceholders() {
        return placeholders;
    }
}
