package cn.lunadeer.deertitle.importer;

public record ImportIssue(Severity severity, String code, String record, String message) {
    public enum Severity { WARNING, ERROR }
}
