package software.amazon.organizations.policy;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.core.retry.backoff.EqualJitterBackoffStrategy;

import software.amazon.cloudformation.LambdaWrapper;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientBuilder {
    // Retry Strategy
    private static final BackoffStrategy BACKOFF_STRATEGY = EqualJitterBackoffStrategy.builder()
                                                                .baseDelay(Duration.ofMillis(2000))
                                                                .maxBackoffTime(Duration.ofMillis(20000))
                                                                .build();
    private static final RetryPolicy ORGANIZATIONS_RETRY_POLICY =
        RetryPolicy.builder()
            .numRetries(4)
            .retryCondition(RetryCondition.defaultRetryCondition())
            .throttlingBackoffStrategy(BACKOFF_STRATEGY)
            .build();

    private ClientBuilder() {
    }

    public static OrganizationsClient getClient() {
        return OrganizationsClient.builder()
                   .overrideConfiguration(ClientOverrideConfiguration.builder()
                                              .retryPolicy(ORGANIZATIONS_RETRY_POLICY)
                                              .build())
                   .region(getGlobalRegion())
                   .httpClient(LambdaWrapper.HTTP_CLIENT)
                   .build();
    }

    /**
     * Returns aws-us-gov-global Region if current region is a gov region.
     * Returns aws-cn-global Region if current region is a china region.
     * Otherwise returns aws-global Region
     *
     * @return AWS Organizations Global Region
     */
    private static Region getGlobalRegion() {
        final String currentRegion = Optional.ofNullable(System.getenv("AWS_REGION")).orElse("");
        final Pattern isGovPattern = Pattern.compile("us-gov");
        final Matcher isGovMatcher = isGovPattern.matcher(currentRegion);
        final Pattern isChinaPattern = Pattern.compile("cn");
        final Matcher isChinaMatcher = isChinaPattern.matcher(currentRegion);

        if (isGovMatcher.find()) {
            return Region.AWS_US_GOV_GLOBAL;
        } else if (isChinaMatcher.find()) {
            return Region.AWS_CN_GLOBAL;
        }
        return Region.AWS_GLOBAL;
    }
}
