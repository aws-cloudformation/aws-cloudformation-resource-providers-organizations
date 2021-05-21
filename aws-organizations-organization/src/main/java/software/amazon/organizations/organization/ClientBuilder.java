package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.OrganizationsClient;

import software.amazon.cloudformation.LambdaWrapper;

public class ClientBuilder {

  public static OrganizationsClient getClient() {
    return OrganizationsClient.builder()
              .httpClient(LambdaWrapper.HTTP_CLIENT)
              .build();
  }
}
