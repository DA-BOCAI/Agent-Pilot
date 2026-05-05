package com.hay.agent.service.preview;

import com.hay.agent.api.dto.GenerateStepPreviewRequest;
import com.hay.agent.api.dto.preview.PresentationPreviewRequest;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.Artifact;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.service.content.ContentPreviewService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PresentationStepPreviewGenerator implements StepPreviewGenerator {
    private static final int MAX_DOC_BRIEF_CHARS = 2400;
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+(.+)$");
    private static final Pattern MARKDOWN_BULLET = Pattern.compile("^(?:[-*+]\\s+|\\d+[.)]\\s+)(.+)$");

    private final ContentPreviewService contentPreviewService;

    public PresentationStepPreviewGenerator(ContentPreviewService contentPreviewService) {
        this.contentPreviewService = contentPreviewService;
    }

    @Override
    public boolean supports(PlanStep step) {
        return step != null && "D_SLIDES".equals(step.getStepId());
    }

    @Override
    public Object generate(AgentTask task, PlanStep step, GenerateStepPreviewRequest request) {
        return contentPreviewService.previewPresentation(PresentationPreviewRequest.builder()
                .userInput(buildSlidesSourceInput(task))
                .topic(step.getAction())
                .theme(request == null ? null : request.getTheme())
                .build());
    }

    private String buildSlidesSourceInput(AgentTask task) {
        String originalInput = task == null || task.getInputText() == null ? "" : task.getInputText();
        Optional<DocPreviewSource> docPreviewSource = findLatestDocPreviewSource(task);
        if (docPreviewSource.isEmpty()) {
            return originalInput;
        }
        DocPreviewSource source = docPreviewSource.get();
        return originalInput
                + "\n\n【Doc → PPT 编排任务】\n"
                + "以下内容来自已经确认的前序文档预览。请不要逐段搬运文档原文，而是把它压缩成适合现场汇报的 PPT：结论先行、页面标题要像观点、每页只承载一个核心信息。\n"
                + "必须优先复用文档中的目标、对象、关键论据、风险、执行步骤和验收口径；如果文档中已有阶段/风险/对比信息，请转成时间线页、风险表或对比页。\n"
                + "PPT 建议结构：封面、目录、核心结论、背景/目标、关键方案或内容展开、执行计划、风险与应对、总结与下一步。\n"
                + "\n【前序文档信息】\n"
                + "文档标题：" + source.title() + "\n"
                + buildDocOutline(source.rawMarkdown())
                + "\n【前序文档精简内容】\n"
                + compactDocMarkdown(source.rawMarkdown());
    }

    private Optional<DocPreviewSource> findLatestDocPreviewSource(AgentTask task) {
        if (task == null || task.getArtifacts() == null) {
            return Optional.empty();
        }
        for (int i = task.getArtifacts().size() - 1; i >= 0; i--) {
            Artifact artifact = task.getArtifacts().get(i);
            if (artifact == null || !"C_DOC".equals(artifact.getStepId()) || artifact.getPreviewData() == null) {
                continue;
            }
            String rawMarkdown = artifact.getPreviewData().path("rawMarkdown").asText("");
            if (!rawMarkdown.isBlank()) {
                String previewTitle = artifact.getPreviewData().path("title").asText("");
                String title = previewTitle.isBlank() ? artifact.getTitle() : previewTitle;
                return Optional.of(new DocPreviewSource(title == null || title.isBlank() ? "前序文档" : title, rawMarkdown));
            }
        }
        return Optional.empty();
    }

    private String buildDocOutline(String markdown) {
        List<String> headings = new ArrayList<>();
        for (String rawLine : safeLines(markdown)) {
            Matcher matcher = MARKDOWN_HEADING.matcher(rawLine.trim());
            if (matcher.matches()) {
                headings.add(cleanInline(matcher.group(1)));
            }
            if (headings.size() >= 8) {
                break;
            }
        }
        if (headings.isEmpty()) {
            return "文档章节：未识别到明确 Markdown 标题\n";
        }
        return "文档章节：" + String.join(" / ", headings) + "\n";
    }

    private String compactDocMarkdown(String markdown) {
        StringBuilder builder = new StringBuilder();
        int sectionCount = 0;
        int bulletCount = 0;
        for (String rawLine : safeLines(markdown)) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank() || line.startsWith("```")) {
                continue;
            }
            Matcher heading = MARKDOWN_HEADING.matcher(line);
            if (heading.matches()) {
                sectionCount++;
                if (sectionCount <= 8) {
                    appendLimited(builder, "\n## " + cleanInline(heading.group(1)) + "\n");
                }
                continue;
            }

            Matcher bullet = MARKDOWN_BULLET.matcher(line);
            if (bullet.matches()) {
                bulletCount++;
                if (bulletCount <= 18) {
                    appendLimited(builder, "- " + compactLine(bullet.group(1), 90) + "\n");
                }
                continue;
            }

            if (builder.length() < MAX_DOC_BRIEF_CHARS) {
                appendLimited(builder, "- " + compactLine(line, 90) + "\n");
            }
            if (builder.length() >= MAX_DOC_BRIEF_CHARS) {
                break;
            }
        }
        if (builder.isEmpty()) {
            return compactLine(markdown, MAX_DOC_BRIEF_CHARS);
        }
        return builder.toString().trim();
    }

    private void appendLimited(StringBuilder builder, String value) {
        if (builder.length() >= MAX_DOC_BRIEF_CHARS || value == null || value.isBlank()) {
            return;
        }
        int remaining = MAX_DOC_BRIEF_CHARS - builder.length();
        builder.append(value, 0, Math.min(remaining, value.length()));
    }

    private List<String> safeLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(text.replace("\r\n", "\n").replace('\r', '\n').split("\n"));
    }

    private String cleanInline(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("__(.+?)__", "$1")
                .replaceAll("`(.+?)`", "$1")
                .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String compactLine(String value, int maxLength) {
        String text = cleanInline(value);
        if (text.length() <= maxLength) {
            return text;
        }
        int split = Math.max(text.lastIndexOf('；', maxLength), text.lastIndexOf('，', maxLength));
        if (split >= 12) {
            return text.substring(0, split).trim();
        }
        return text.substring(0, Math.max(1, maxLength - 1)).trim() + "...";
    }

    @Override
    public String previewArtifactType() {
        return "slides-preview";
    }

    @Override
    public String previewTitle() {
        return "PPT预览";
    }

    @Override
    public String expectedPreviewDataType() {
        return "PRESENTATION";
    }

    private record DocPreviewSource(String title, String rawMarkdown) {
    }
}
