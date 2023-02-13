package io.managed.services.test.cli;

public enum ACLEntityType {
    USER("--user"),
    SERVICE_ACCOUNT("--service-account");

    public final String label;

    private ACLEntityType(String label) {
        this.label = label;
    }
}
