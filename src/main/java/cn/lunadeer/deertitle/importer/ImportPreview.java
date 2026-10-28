package cn.lunadeer.deertitle.importer;

import cn.lunadeer.deertitle.domain.ImportBatch;
import java.time.Instant;
import java.util.List;

/** A reviewed immutable source snapshot; executing it never rereads changed source rows. */
public record ImportPreview(ImportBatch batch, List<ImportIssue> issues,
                            String sourceFingerprint, Instant readAt) {
    public ImportPreview {
        issues = List.copyOf(issues);
    }

    public boolean valid() {
        return issues.stream().noneMatch(issue -> issue.severity() == ImportIssue.Severity.ERROR);
    }

    public long warnings() {
        return issues.stream().filter(issue -> issue.severity() == ImportIssue.Severity.WARNING).count();
    }

    public long errors() {
        return issues.stream().filter(issue -> issue.severity() == ImportIssue.Severity.ERROR).count();
    }
}
