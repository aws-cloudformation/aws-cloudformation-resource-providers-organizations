package software.amazon.organizations.account;

public class AccountConstants {
    public enum Action {
        CREATE_ACCOUNT,
        MOVE_ACCOUNT,
        CLOSE_ACCOUNT,
        TAG_RESOURCE,
        UNTAG_RESOURCE,
        LIST_PARENTS,
        LIST_TAGS_FOR_RESOURCE,
        DESCRIBE_ACCOUNT,
        LIST_ACCOUNTS
    }

    public enum Handler {
        CREATE,
        DELETE,
        UPDATE,
        READ,
        LIST
    }
}
