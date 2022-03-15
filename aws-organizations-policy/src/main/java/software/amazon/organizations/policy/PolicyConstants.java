package software.amazon.organizations.policy;

public class PolicyConstants {

    public enum POLICY_TYPE {
        AISERVICES_OPT_OUT_POLICY("AISERVICES_OPT_OUT_POLICY"),
        BACKUP_POLICY("BACKUP_POLICY"),
        SERVICE_CONTROL_POLICY("SERVICE_CONTROL_POLICY"),
        TAG_POLICY("TAG_POLICY");

        private final String policyType;

        private POLICY_TYPE(final String policyType) {
            this.policyType = policyType;
        }

        @Override
        public String toString() {
            return policyType;
        }

//        public static final String AISERVICES_OPT_OUT_POLICY ="AISERVICES_OPT_OUT_POLICY";
//        public static final String BACKUP_POLICY ="BACKUP_POLICY";
//        public static final String SERVICE_CONTROL_POLICY ="SERVICE_CONTROL_POLICY";
//        public static final String TAG_POLICY ="TAG_POLICY";
    }
}
