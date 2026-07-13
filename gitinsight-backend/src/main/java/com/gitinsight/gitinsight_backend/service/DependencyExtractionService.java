package com.gitinsight.gitinsight_backend.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.gitinsight.gitinsight_backend.entity.ClassDependency;
import com.gitinsight.gitinsight_backend.entity.GitRepository;
import com.gitinsight.gitinsight_backend.repository.ClassDependencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DependencyExtractionService {

    private static final Logger log = LoggerFactory.getLogger(DependencyExtractionService.class);
    private final ClassDependencyRepository classDependencyRepository;

    public DependencyExtractionService(ClassDependencyRepository classDependencyRepository) {
        this.classDependencyRepository = classDependencyRepository;
    }

    @Transactional
    public void extractAndSaveDependencies(GitRepository repository) {
        log.info("Starting Dependency Extraction for repository: {}", repository.getName());

        // 1. Clear old dependencies to prevent duplicates on re-analysis
        classDependencyRepository.deleteByRepositoryId(repository.getId());

        Path repoPath = Paths.get(repository.getLocalPath());

        // 2. Walk the file tree and find all .java files
        try (Stream<Path> paths = Files.walk(repoPath)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> parseAndSave(path, repository));

            log.info("Dependency Extraction complete.");
        } catch (Exception e) {
            log.error("Failed to walk repository path: {}", e.getMessage());
        }
    }

    private void parseAndSave(Path filePath, GitRepository repository) {
        try {
            // 3. Parse the file into an Abstract Syntax Tree (AST)
            CompilationUnit cu = StaticJavaParser.parse(filePath);

            // 4. Extract the Class Name (Default to filename if missing)
            String className = cu.getPrimaryTypeName()
                    .orElse(filePath.getFileName().toString().replace(".java", ""));

            // 5. Extract all Imports (These are the dependencies!)
            // We filter out standard Java/Spring libraries so we only map internal project dependencies
            List<String> imports = cu.getImports().stream()
                    .map(ImportDeclaration::getNameAsString)
                    .filter(name -> !name.startsWith("java.") && !name.startsWith("javax."))
                    .collect(Collectors.toList());

            // If it doesn't import anything, we still save it as a standalone node
            String dependenciesString = String.join(",", imports);

            // 6. Save to PostgreSQL
            ClassDependency dependencyNode = ClassDependency.builder()
                    .repository(repository)
                    .className(className)
                    .filePath(filePath.toString())
                    .dependencies(dependenciesString)
                    .build();

            classDependencyRepository.save(dependencyNode);

        } catch (Exception e) {
            // Some files might be malformed or unparseable; we just skip them and log it.
            log.debug("Could not parse file: {}", filePath.getFileName());
        }
    }
}