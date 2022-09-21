package software.amazon.organizations.account;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.organizations.OrganizationsClient;

import static org.assertj.core.api.Assertions.assertThat;

public class ClientBuilderTest {

    private static final String ORG_CLIENT = "OrganizationsClient";

    @Test
    public void testCreateClient() {
        OrganizationsClient client = ClientBuilder.getClient();
        assertThat(client).isNotNull();
        assertThat(client.toString().contains(ORG_CLIENT)).isTrue();
    }
}
