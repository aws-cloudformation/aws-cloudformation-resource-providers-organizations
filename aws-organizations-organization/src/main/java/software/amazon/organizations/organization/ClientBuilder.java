package software.amazon.organizations.organization;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.retry.backoff.EqualJitterBackoffStrategy;
import software.amazon.awssdk.core.retry.conditions.OrRetryCondition;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;
import software.amazon.awssdk.core.retry.conditions.RetryOnExceptionsCondition;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.organizations.OrganizationsClient;

import software.amazon.awssdk.services.organizations.model.ConcurrentModificationException;
import software.amazon.awssdk.services.organizations.model.ServiceException;
import software.amazon.awssdk.services.organizations.model.TooManyRequestsException;
import software.amazon.cloudformation.LambdaWrapper;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientBuilder {
    private ClientBuilder() {
    }
    // Retry Strategy
    private static final int MAX_ERROR_RETRY = 3;
    private static final BackoffStrategy BACKOFF_STRATEGY = EqualJitterBackoffStrategy.builder()
            .baseDelay(Duration.ofMillis(500))
            .maxBackoffTime(Duration.ofMillis(5000))
            .build();

    private static final BackoffStrategy THROTTLE_BACKOFF_STRATEGY = EqualJitterBackoffStrategy.builder()
            .baseDelay(Duration.ofMillis(1000))
            .maxBackoffTime(Duration.ofMillis(10000))
            .build();

    // Retry customized conditions
    private static final RetryCondition retryCondition = OrRetryCondition.create(
            RetryCondition.defaultRetryCondition(),
            RetryOnExceptionsCondition.create(Collections.singleton(ConcurrentModificationException.class)),
            RetryOnExceptionsCondition.create(Collections.singleton(TooManyRequestsException.class)),
            RetryOnExceptionsCondition.create(Collections.singleton(ServiceException.class))
    );

    private static final RetryPolicy ORGANIZATIONS_RETRY_POLICY =
            RetryPolicy.builder()
                    .numRetries(MAX_ERROR_RETRY)
                    .retryCondition(retryCondition)
                    .backoffStrategy(BACKOFF_STRATEGY)
                    .throttlingBackoffStrategy(THROTTLE_BACKOFF_STRATEGY)
                    .build();

    public static OrganizationsClient getClient() {
        return OrganizationsClient.builder()
                .httpClient(LambdaWrapper.HTTP_CLIENT)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .retryPolicy(ORGANIZATIONS_RETRY_POLICY)
                        .build())
                .region(getGlobalRegion())
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
