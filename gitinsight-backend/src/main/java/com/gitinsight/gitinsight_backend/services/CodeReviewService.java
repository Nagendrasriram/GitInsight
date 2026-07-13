package com.gitinsight.gitinsight_backend.services;

import com.gitinsight.gitinsight_backend.entity.GitRepository;
import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Service
public class CodeReviewService {

    private final GitRepositoryRepository gitRepositoryRepository;
    private final AiOrchestrationService aiOrchestrationService;

    public CodeReviewService(GitRepositoryRepository gitRepositoryRepository,
                             AiOrchestrationService aiOrchestrationService) {
        this.gitRepositoryRepository = gitRepositoryRepository;
        this.aiOrchestrationService = aiOrchestrationService;
    }

    public String performDeepCodeReview(String url) {
        // 1. Find the repository in the database
        GitRepository repository = gitRepositoryRepository.findByUrl(url)
                .orElseThrow(() -> new RuntimeException("Repository not found. Please generate the summary first."));

        // 2. Locate the physically downloaded files on the server
        String localFolderPath = repository.getLocalPath();
        StringBuilder codeContext = new StringBuilder();
        codeContext.append("Repository: ").append(repository.getName()).append("\n\n");

        // 3. Scan the folder for code files (upgraded to catch files without extensions!)
        // 3. Scan the folder for code files (Upgraded to accept ANY text-based file!)
        try (Stream<Path> paths = Files.walk(Paths.get(localFolderPath))) {

            paths.filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains(".git")) // Ignore hidden git files
                    .filter(path -> !path.toString().endsWith(".class")) // Ignore compiled java files
                    .filter(path -> !path.toString().endsWith(".jar")) // Ignore java archives
                    .limit(20)
                    .forEach(path -> {
                        try {
                            String fileContent = Files.readString(path);
                            codeContext.append("--- File: ").append(path.getFileName()).append(" ---\n");
                            codeContext.append(fileContent).append("\n\n");
                        } catch (IOException e) {
                            System.out.println("Could not read file: " + path.getFileName());
                        }
                    });

        } catch (IOException e) {
            throw new RuntimeException("Failed to read repository files from disk.", e);
        }

        // Safety Check: Did we actually find any code?
        if (codeContext.toString().trim().equals("Repository: " + repository.getName())) {
            return "⚠️ **Analysis Failed:** I could not find any readable code files in this repository. Ensure your files contain text and are not hidden.";
        }

        // 4. Create the new Code Reviewer Persona
        String systemInstruction = "You are a strict, highly experienced Senior Staff Software Engineer performing a code review. " +
                "Review the provided source code. Point out any clever implementations, but focus strictly on finding: " +
                "1. Big-O Time and Space Complexity inefficiencies. " +
                "2. Potential null pointer exceptions or edge-case bugs. " +
                "3. Clean code violations. " +
                "Format your response in clean Markdown with bullet points.";

        System.out.println("🔍 Sending source code to AI for Deep Review...");

        // 5. Send the actual code to Gemini!
        return aiOrchestrationService.generateAnalysis(systemInstruction, codeContext.toString());
    }
}