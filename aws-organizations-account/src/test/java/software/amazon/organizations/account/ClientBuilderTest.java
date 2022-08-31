package software.amazon.organizations.account;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.account.AccountClient;
import software.amazon.awssdk.services.organizations.OrganizationsClient;

import static org.assertj.core.api.Assertions.assertThat;

public class ClientBuilderTest {

    private static final String ORG_CLIENT = "OrganizationsClient";
    private static final String ACCOUNT_CLIENT = "AccountClient";

    @Test
    public void testCreateClient() {
        OrganizationsClient client = ClientBuilder.getClient();
        AccountClient accountClient = ClientBuilder.getAccountClient();
        assertThat(client).isNotNull();
        assertThat(client.toString().contains(ORG_CLIENT)).isTrue();
        assertThat(accountClient).isNotNull();
        assertThat(accountClient.toString().contains(ACCOUNT_CLIENT)).isTrue();
    }
}
