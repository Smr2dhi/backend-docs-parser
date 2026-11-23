
package org.example.sourcecodeanalyser.service;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

import org.example.sourcecodeanalyser.analyzer.JavaClassAnalyzer;
import org.example.sourcecodeanalyser.extractor.JavaSourceExtractor;
import org.example.sourcecodeanalyser.model.JavaAnalysisResult;
import org.example.sourcecodeanalyser.model.JavaClassInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class JavaAnalysisService {

    private static final Logger log =
            LoggerFactory.getLogger(JavaAnalysisService.class);

    private final JavaSourceExtractor javaSourceExtractor;
    private final JavaClassAnalyzer javaClassAnalyzer;

    public JavaAnalysisService(
            JavaSourceExtractor javaSourceExtractor,
            JavaClassAnalyzer javaClassAnalyzer) {

        this.javaSourceExtractor = javaSourceExtractor;
        this.javaClassAnalyzer = javaClassAnalyzer;
    }

    public JavaAnalysisResult analyzeJavaFiles(
            List<Path> javaFiles) {

        List<JavaClassInfo> allClassInfos = new ArrayList<>();

        // Store successfully parsed Java files.
        Map<Path, CompilationUnit> parsedFiles = new HashMap<>();

        // Store fully qualified names of project classes.
        Set<String> projectTypes = new HashSet<>();

        // PASS 1: Parse files and collect project class names.
        for (Path javaFile : javaFiles) {

            try {
                CompilationUnit compilationUnit =
                        javaSourceExtractor.extract(javaFile);

                parsedFiles.put(javaFile, compilationUnit);

                String packageName = compilationUnit
                        .getPackageDeclaration()
                        .map(packageDeclaration ->
                                packageDeclaration.getNameAsString())
                        .orElse("");

                for (TypeDeclaration<?> type :
                        compilationUnit.getTypes()) {

                    String typeName = type.getNameAsString();

                    String fullyQualifiedName = packageName.isEmpty()
                            ? typeName
                            : packageName + "." + typeName;

                    projectTypes.add(fullyQualifiedName);
                }

            } catch (Exception e) {

                log.warn(
                        "Skipping Java file {} because parsing failed",
                        javaFile,
                        e
                );
            }
        }

        // Count files successfully analyzed in Pass 2.
        int successfullyAnalyzedFiles = 0;

        // PASS 2: Analyze only successfully parsed files.
        for (Map.Entry<Path, CompilationUnit> entry :
                parsedFiles.entrySet()) {

            Path javaFile = entry.getKey();
            CompilationUnit compilationUnit = entry.getValue();

            try {
                List<JavaClassInfo> classInfos =
                        javaClassAnalyzer.analyze(
                                compilationUnit,
                                javaFile,
                                projectTypes
                        );

                allClassInfos.addAll(classInfos);

                // Increment only after successful analysis.
                successfullyAnalyzedFiles++;

            } catch (Exception e) {

                log.warn(
                        "Skipping Java file {} because analysis failed",
                        javaFile,
                        e
                );
            }
        }

        // Create the final Java analysis result.
        return new JavaAnalysisResult(
                successfullyAnalyzedFiles,
                allClassInfos
        );
    }
}
