package software.amazon.organizations.organization;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.organizations.OrganizationsClient;

import software.amazon.cloudformation.LambdaWrapper;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientBuilder {
    private ClientBuilder() {}

    public static OrganizationsClient getClient() {
        return OrganizationsClient.builder()
             .region(getGlobalRegion())
             .httpClient(LambdaWrapper.HTTP_CLIENT)
             .build();
    }

  /**
   * Returns aws-us-gov-global Region if current region is a gov region.
   * Otherwise returns aws-global Region
   * @return AWS Organizations Global Region
   */
    private static Region getGlobalRegion() {
        final String currentRegion = Optional.ofNullable(System.getenv("AWS_REGION")).orElse("");
        final Pattern isGovPattern = Pattern.compile("us-gov");
        final Matcher isGovMatcher = isGovPattern.matcher(currentRegion);
        return isGovMatcher.find() ? Region.AWS_US_GOV_GLOBAL : Region.AWS_GLOBAL;
    }
}
