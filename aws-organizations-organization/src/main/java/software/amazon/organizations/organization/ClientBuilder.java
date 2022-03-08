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
   * Returns aws-cn-global Region if current region is a china region.
   * Otherwise returns aws-global Region
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
