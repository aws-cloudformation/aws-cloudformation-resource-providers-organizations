package software.amazon.organizations.policy;

public class PolicyConstants {

    public enum PolicyType {
        AISERVICES_OPT_OUT_POLICY("AISERVICES_OPT_OUT_POLICY"),
        BACKUP_POLICY("BACKUP_POLICY"),
        SERVICE_CONTROL_POLICY("SERVICE_CONTROL_POLICY"),
        TAG_POLICY("TAG_POLICY");

        private final String policyType;

        private PolicyType(final String policyType) {
            this.policyType = policyType;
        }

        @Override
        public String toString() {
            return policyType;
        }
    }

    // constants used for handleRetriableException
    public enum Action {
        ATTACH_POLICY,
        DETACH_POLICY,
        DELETE_POLICY
    }
}
