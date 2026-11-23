package backend.hr;

public enum LeaveType {
    ANNUAL(14), CASUAL(7), SICK(7), UNPAID(null), OTHER(null);

    private final Integer entitlement;

    LeaveType(Integer entitlement) {
        this.entitlement = entitlement;
    }

    /** Days per year, or null when the type has no limit. */
    public Integer getEntitlement() {
        return entitlement;
    }
}