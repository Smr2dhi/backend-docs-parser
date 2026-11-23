
package org.example.sourcecodeanalyser.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class RepositoryScanner {

    private static final Logger log =
            LoggerFactory.getLogger(RepositoryScanner.class);

    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git",
            ".gradle",
            ".mvn",
            ".idea",
            ".vscode",
            "build",
            "target",
            "dist",
            "out",
            "node_modules",
            "vendor",
            "vendors",
            "third-party",
            "third_party"
    );

    private static final List<String> EXCLUDED_FILE_SUFFIXES = List.of(
            ".min.css",
            ".min.js",
            ".bundle.js",
            ".bundle.css",
            ".map"
    );

    public List<Path> scanRepository(Path repositoryPath)
            throws IOException {

        if (repositoryPath == null) {
            throw new IllegalArgumentException(
                    "Repository path cannot be null"
            );
        }

        Path rootPath = repositoryPath.toAbsolutePath().normalize();

        if (!Files.isDirectory(rootPath)) {
            throw new IllegalArgumentException(
                    "Invalid repository directory: " + rootPath
            );
        }

        List<Path> discoveredFiles = new ArrayList<>();

        Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory,
                    BasicFileAttributes attributes) {

                if (!directory.equals(rootPath)) {

                    String directoryName = directory
                            .getFileName()
                            .toString()
                            .toLowerCase(Locale.ROOT);

                    if (EXCLUDED_DIRECTORIES.contains(directoryName)) {

                        log.debug(
                                "Skipping excluded directory: {}",
                                directory
                        );

                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(
                    Path file,
                    BasicFileAttributes attributes) {

                if (attributes.isRegularFile() && !isExcludedFile(file)) {
                    discoveredFiles.add(file);
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(
                    Path file,
                    IOException exception) {

                log.warn(
                        "Skipping inaccessible path: {}. Reason: {}",
                        file,
                        exception.getMessage()
                );

                return FileVisitResult.CONTINUE;
            }
        });

        discoveredFiles.sort(
                Comparator
                        .comparing(
                                (Path path) -> rootPath
                                        .relativize(path)
                                        .toString()
                                        .toLowerCase(Locale.ROOT)
                        )
                        .thenComparing(
                                path -> rootPath.relativize(path).toString()
                        )
        );

        log.info(
                "Repository scan completed. Discovered {} files in {}",
                discoveredFiles.size(),
                rootPath
        );

        return discoveredFiles;
    }

    private boolean isExcludedFile(Path file) {

        String fileName = file.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        for (String suffix : EXCLUDED_FILE_SUFFIXES) {

            if (fileName.endsWith(suffix)) {
                return true;
            }
        }

        return false;
    }
}
