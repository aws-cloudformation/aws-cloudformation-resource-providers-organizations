package software.amazon.organizations.resourcepolicy;

public class ResourcePolicyConstants {
    // constants used for handleRetriableException
    public enum Action {
        CREATE_RESOURCEPOLICY,
        DESCRIBE_RESOURCEPOLICY,
        DELETE_RESOURCEPOLICY,
        UPDATE_RESOURCEPOLICY,
        TAG_RESOURCE,
        UNTAG_RESOURCE,
        LIST_TAGS_FOR_RESOURCEPOLICY,
        LIST_RESOURCEPOLICY
    }

    public enum Handler {
        CREATE,
        DELETE,
        UPDATE,
        READ,
        LIST
    }
}
