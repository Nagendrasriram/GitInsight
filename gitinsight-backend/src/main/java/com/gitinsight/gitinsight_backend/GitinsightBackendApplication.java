package com.gitinsight.gitinsight_backend;

import com.gitinsight.gitinsight_backend.service.CommitExtractionService;
import com.gitinsight.gitinsight_backend.service.ContributorAnalyticsService;
import com.gitinsight.gitinsight_backend.service.GitCloningService;
import com.gitinsight.gitinsight_backend.service.OwnershipAnalyticsService;
import org.springframework.ai.model.openai.autoconfigure.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
		OpenAiChatAutoConfiguration.class,
		OpenAiEmbeddingAutoConfiguration.class,
		OpenAiAudioSpeechAutoConfiguration.class,
		OpenAiAudioTranscriptionAutoConfiguration.class,
		OpenAiModerationAutoConfiguration.class,
		OpenAiImageAutoConfiguration.class
})
public class GitinsightBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(GitinsightBackendApplication.class, args);
	}
}//package com.gitinsight.gitinsight_backend;
//
//import com.gitinsight.gitinsight_backend.service.CommitExtractionService;
//import com.gitinsight.gitinsight_backend.service.ContributorAnalyticsService;
//import com.gitinsight.gitinsight_backend.service.GitCloningService;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.context.annotation.Bean;
//
//@SpringBootApplication
//public class GitinsightBackendApplication {
//
//	public static void main(String[] args) {
//		SpringApplication.run(GitinsightBackendApplication.class, args);
//	}
//
//	@Bean
//	public CommandLineRunner structuralIntegrationTestRunner(
//			GitCloningService cloningService,
//			CommitExtractionService commitExtractionService,
//			ContributorAnalyticsService contributorAnalyticsService // <-- Injecting our new Analytics Service
//	) {
//		return args -> {
//			System.out.println("====== STARTING DAY 4 INTEGRATION TEST ======");
//			try {
//				String targetTestUrl = "https://github.com/octocat/Spoon-Knife.git";
//
//				// 1. Clone the repository
//				var recordedMetadata = cloningService.cloneAndRegister(targetTestUrl);
//
//				// 2. Extract the commits (If they already exist, this is safe to run again)
//				commitExtractionService.extractAndSaveCommits(recordedMetadata);
//
//				// 3. Generate Analytics (Day 4)
//				contributorAnalyticsService.generateContributorMetrics(recordedMetadata);
//
//				System.out.println("=============================================");
//			} catch (Exception exception) {
//				System.err.println("Test execution failed: " + exception.getMessage());
//			}
//		};
//	}
//}


//package com.gitinsight.gitinsight_backend;
//
//import com.gitinsight.gitinsight_backend.service.CommitExtractionService;
//import com.gitinsight.gitinsight_backend.service.ContributorAnalyticsService;
//import com.gitinsight.gitinsight_backend.service.GitCloningService;
//import com.gitinsight.gitinsight_backend.service.OwnershipAnalyticsService;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.context.annotation.Bean;
//
//@SpringBootApplication
//public class GitinsightBackendApplication {
//
//	public static void main(String[] args) {
//		SpringApplication.run(GitinsightBackendApplication.class, args);
//	}
//
//	@Bean
//	public CommandLineRunner structuralIntegrationTestRunner(
//			GitCloningService cloningService,
//			CommitExtractionService commitExtractionService,
//			ContributorAnalyticsService contributorAnalyticsService,
//			OwnershipAnalyticsService ownershipAnalyticsService // <-- Injecting the new service
//	) {
//		return args -> {
//			System.out.println("====== STARTING DAY 4 PART 2 INTEGRATION TEST ======");
//			try {
//				String targetTestUrl = "https://github.com/octocat/Spoon-Knife.git";
//
//				var recordedMetadata = cloningService.cloneAndRegister(targetTestUrl);
//				commitExtractionService.extractAndSaveCommits(recordedMetadata);
//				contributorAnalyticsService.generateContributorMetrics(recordedMetadata);
//
//				// 4. Calculate File Ownership
//				ownershipAnalyticsService.calculateFileOwnership(recordedMetadata);
//
//				System.out.println("====================================================");
//			} catch (Exception exception) {
//				System.err.println("Test execution failed: " + exception.getMessage());
//			}
//		};
//	}
