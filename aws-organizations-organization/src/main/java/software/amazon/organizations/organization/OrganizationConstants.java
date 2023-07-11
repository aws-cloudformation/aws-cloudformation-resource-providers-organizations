package software.amazon.organizations.organization;

public class OrganizationConstants {
    // constants used for handleRetriableException
    public enum Action {
        CREATE_ORG,
        DELETE_ORG,
        GETROOT_ID,
        DESCRIBE_ORG

    }

    public enum Handler {
        CREATE,
        DELETE,
        READ,
        LIST

    }
}
