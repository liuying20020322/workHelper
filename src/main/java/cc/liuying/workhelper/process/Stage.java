package cc.liuying.workhelper.process;

public enum Stage {
    ASSESSMENT, WRITTEN_TEST, INTERVIEW_1, INTERVIEW_2, INTERVIEW_3, OFFER, REJECTED, WITHDRAWN;

    public boolean isResult() { return this == OFFER || this == REJECTED || this == WITHDRAWN; }
    public boolean isInterview() { return name().startsWith("INTERVIEW_"); }
    public String defaultRoundName() {
        return switch (this) {
            case INTERVIEW_1 -> "一面";
            case INTERVIEW_2 -> "二面";
            case INTERVIEW_3 -> "三面";
            default -> "";
        };
    }
}
