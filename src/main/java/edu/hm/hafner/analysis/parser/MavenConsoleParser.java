package edu.hm.hafner.analysis.parser;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.analysis.FastRegexpLineParser;
import edu.hm.hafner.analysis.Issue;
import edu.hm.hafner.analysis.IssueBuilder;
import edu.hm.hafner.analysis.Report;
import edu.hm.hafner.analysis.Priority;

/**
 * A parser for maven console warnings.
 *
 * @author Ullrich Hafner
 */
public class MavenConsoleParser extends FastRegexpLineParser {
    private static final String WARNING = "WARNING";
    private static final String ERROR = "ERROR";
    private static final int MAX_MESSAGE_LENGTH = 4000;

    private static final long serialVersionUID = 1737791073711198075L;

    private static final String PATTERN = "^.*\\[(WARNING|ERROR)\\]\\s*(.*)$";

    /**
     * Creates a new instance of {@link MavenConsoleParser}.
     */
    public MavenConsoleParser() {
        super(PATTERN);
    }

    @Override
    protected boolean isLineInteresting(final String line) {
        return line.contains(WARNING) || line.contains(ERROR);
    }

    @Override
    protected Issue createIssue(final Matcher matcher, final IssueBuilder builder) {
        Priority priority;
        String category;
        if (ERROR.equals(matcher.group(1))) {
            priority = Priority.HIGH;
            category = "Error";
        }
        else {
            priority = Priority.NORMAL;
            category = "Warning";
        }
        return builder.setFileName(SELF).setLineStart(getCurrentLine()).setCategory(category)
                .setMessage(matcher.group(2)).setPriority(priority).build();
    }

    // TODO: post processing is quite slow for large number of warnings, see JENKINS-25278
    @Override
    protected Report postProcess(final Report warnings) {
        IssueBuilder builder = new IssueBuilder();
        Deque<Issue> condensed = new ArrayDeque<>();
        int line = -1;
        for (Issue warning : warnings) {
            if (warning.getLineStart() == line + 1 && !condensed.isEmpty()) {
                Issue previous = condensed.getLast();
                if (previous.getSeverity().equals(warning.getSeverity())) {
                    condensed.removeLast();
                    if (previous.getMessage().length() + warning.getMessage().length() >= MAX_MESSAGE_LENGTH) {
                        condensed.add(builder.copy(previous).setLineStart(warning.getLineStart()).build());
                    }
                    else {
                        condensed.add(builder.copy(previous).setLineStart(warning.getLineStart())
                                .setMessage(previous.getMessage() + "\n" + warning.getMessage())
                                .build());
                    }
                }
                else {
                    condensed.add(warning);
                }
            }
            else {
                condensed.add(warning);
            }
            line = warning.getLineStart();
        }
        Report noBlank = new Report();
        for (Issue warning : condensed) {
            if (StringUtils.isNotBlank(warning.getMessage())) {
                noBlank.add(warning);
            }
        }
        return noBlank;
    }
}

