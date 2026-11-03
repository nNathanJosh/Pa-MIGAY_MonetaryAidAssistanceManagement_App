package ph.pamigay.app;

public enum EntityType {
    BENEFICIARY("Beneficiaries", "People and households receiving assistance"),
    PAYOUT("Payouts", "Scheduled distribution programs and their funding"),
    RECEIPT("Claims", "Beneficiary claims recorded against a payout"),
    DISTRICT("Districts", "Districts used to organize service locations"),
    BARANGAY("Barangays", "Barangays linked to a district"),
    CRISIS("Crises", "Events that qualify a distribution"),
    STAFF("Staff", "Staff members responsible for distributions"),
    ANNOUNCEMENT("Announcements", "Public notices linked to a payout");

    private final String title;
    private final String description;

    EntityType(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String title() { return title; }
    public String description() { return description; }
}
