package software.amazon.organizations.organizationalunit;

public class Constants {
    // constants used for handleRetriableException
    public enum Action {
        CREATE_OU,
        DELETE_OU,
        UPDATE_OU,
        TAG_RESOURCE,
        UNTAG_RESOURCE,
        DESCRIBE_OU,
        LIST_PARENTS,
        LIST_TAGS_FOR_OU,
        LIST_OU_FOR_PARENT
    }

    public enum Handler {
        CREATE,
        DELETE,
        UPDATE,
        READ,
        LIST
    }
}
