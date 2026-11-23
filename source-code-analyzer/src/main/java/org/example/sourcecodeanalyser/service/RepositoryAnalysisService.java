
package org.example.sourcecodeanalyser.service;

import org.example.sourcecodeanalyser.extractor.CSSSourceExtractor;
import org.example.sourcecodeanalyser.extractor.SCSSSourceExtractor;

import org.example.sourcecodeanalyser.model.CSSAnalysisResult;
import org.example.sourcecodeanalyser.model.SCSSAnalysisResult;
import org.example.sourcecodeanalyser.model.FileDiscoveryResult;
import org.example.sourcecodeanalyser.model.JavaAnalysisResult;
import org.example.sourcecodeanalyser.model.RepositoryAnalysisResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RepositoryAnalysisService {

    private static final Logger log =
            LoggerFactory.getLogger(RepositoryAnalysisService.class);

    private final RepositoryScanService repositoryScanService;
    private final JavaAnalysisService javaAnalysisService;
    private final CSSSourceExtractor cssSourceExtractor;
    private final SCSSSourceExtractor scssSourceExtractor;

    public RepositoryAnalysisService(
            RepositoryScanService repositoryScanService,
            JavaAnalysisService javaAnalysisService,
            CSSSourceExtractor cssSourceExtractor,
            SCSSSourceExtractor scssSourceExtractor) {

        this.repositoryScanService = repositoryScanService;
        this.javaAnalysisService = javaAnalysisService;
        this.cssSourceExtractor = cssSourceExtractor;
        this.scssSourceExtractor = scssSourceExtractor;
    }

    public RepositoryAnalysisResult analyzeRepository(
            Path repositoryPath) throws IOException {

        // 1. Discover repository files.
        FileDiscoveryResult fileDiscovery =
                repositoryScanService.scanRepository(repositoryPath);

        // 2. Analyze Java files.
        List<Path> javaFiles =
                fileDiscovery.getCategorizedFiles()
                        .getOrDefault("JAVA", List.of());

        JavaAnalysisResult javaAnalysis =
                javaAnalysisService.analyzeJavaFiles(javaFiles);

        // 3. Analyze CSS files independently.
        List<Path> cssFiles =
                fileDiscovery.getCategorizedFiles()
                        .getOrDefault("CSS", List.of());

        List<CSSAnalysisResult> cssAnalysis = new ArrayList<>();

        for (Path cssFile : cssFiles) {

            try {

                CSSAnalysisResult result =
                        cssSourceExtractor.extract(cssFile);

                cssAnalysis.add(result);

            } catch (Exception exception) {

                log.warn(
                        "Skipping CSS file {} because extraction failed: {}",
                        cssFile,
                        exception.getMessage(),
                        exception
                );
            }
        }

        // 4. Analyze SCSS files independently.
        List<Path> stylesheetFiles =
                fileDiscovery.getCategorizedFiles()
                        .getOrDefault("STYLESHEETS", List.of());

        List<SCSSAnalysisResult> scssAnalysis = new ArrayList<>();

        for (Path stylesheetFile : stylesheetFiles) {

            String fileName = stylesheetFile
                    .getFileName()
                    .toString()
                    .toLowerCase(Locale.ROOT);

            if (!fileName.endsWith(".scss")) {
                continue;
            }

            try {

                SCSSAnalysisResult result =
                        scssSourceExtractor.extract(stylesheetFile);

                scssAnalysis.add(result);

            } catch (Exception exception) {

                log.warn(
                        "Skipping SCSS file {} because extraction failed: {}",
                        stylesheetFile,
                        exception.getMessage(),
                        exception
                );
            }
        }

        // 5. Return combined analysis.
        log.info(
                "Repository analysis completed. Java files: {}, CSS files: {}, SCSS files: {}",
                javaAnalysis.getTotalJavaFiles(),
                cssAnalysis.size(),
                scssAnalysis.size()
        );

        return new RepositoryAnalysisResult(
                fileDiscovery,
                javaAnalysis,
                cssAnalysis,
                scssAnalysis
        );
    }
}
