package software.amazon.organizations.resourcepolicy;

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

public class ClientBuilder {
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
        String region = System.getenv("AWS_REGION");

        return OrganizationsClient.builder()
                   .httpClient(LambdaWrapper.HTTP_CLIENT)
                   .overrideConfiguration(ClientOverrideConfiguration.builder()
                                              .retryPolicy(ORGANIZATIONS_RETRY_POLICY)
                                              .build())
                   .region(Region.of(region))
                   .build();
    }
}
