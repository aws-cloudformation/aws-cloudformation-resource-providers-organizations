package software.amazon.organizations.organizationalunit;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.cloudformation.LambdaWrapper;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientBuilder {
  public static OrganizationsClient getClient() {
    String region = System.getenv("AWS_REGION");

    return OrganizationsClient.builder()
            .httpClient(LambdaWrapper.HTTP_CLIENT)
            .region(getGlobalRegion(region))
            .build();
  }

  /**
   * Returns aws-us-gov-global Region if current region is a gov region.
   * Returns aws-cn-global Region if current region is a china region.
   * Otherwise returns aws-global Region
   * @param region current AWS region {@link ResourceHandlerRequest#getRegion()}
   * @return IAM Global Region
   */
  private static Region getGlobalRegion(final String region) {
      final String currentRegion = Optional.ofNullable(region).orElse("");
      final Pattern isGovPattern = Pattern.compile("us-gov");
      final Pattern isChinaPattern = Pattern.compile("cn");

      final Matcher isGovMatcher = isGovPattern.matcher(currentRegion);
      final Matcher isChinaMatcher = isChinaPattern.matcher(currentRegion);

      if (isGovMatcher.find()) {
          return Region.AWS_US_GOV_GLOBAL;
      } else if (isChinaMatcher.find()) {
          return Region.AWS_CN_GLOBAL;
      }

      return Region.AWS_GLOBAL;
  }

}
