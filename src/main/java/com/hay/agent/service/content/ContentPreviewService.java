package com.hay.agent.service.content;

import com.hay.agent.api.dto.preview.DocumentPreviewRequest;
import com.hay.agent.api.dto.preview.DocumentPreviewResponse;
import com.hay.agent.api.dto.preview.PresentationPreviewRequest;
import com.hay.agent.api.dto.preview.PresentationPreviewResponse;
import com.hay.agent.service.presentation.PresentationMarkdownParser;
import com.hay.agent.service.presentation.PresentationDesignAdvisor;
import com.hay.agent.service.presentation.PresentationSlide;
import com.hay.agent.service.presentation.PresentationTheme;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ContentPreviewService {

    private final ContentGeneratorService contentGeneratorService;
    private final MarkdownPreviewParser markdownPreviewParser;
    private final PresentationMarkdownParser presentationMarkdownParser;
    private final PresentationDesignAdvisor presentationDesignAdvisor;

    public ContentPreviewService(ContentGeneratorService contentGeneratorService,
                                 MarkdownPreviewParser markdownPreviewParser,
                                 PresentationMarkdownParser presentationMarkdownParser,
                                 PresentationDesignAdvisor presentationDesignAdvisor) {
        this.contentGeneratorService = contentGeneratorService;
        this.markdownPreviewParser = markdownPreviewParser;
        this.presentationMarkdownParser = presentationMarkdownParser;
        this.presentationDesignAdvisor = presentationDesignAdvisor;
    }

    public DocumentPreviewResponse previewDocument(DocumentPreviewRequest request) {
        String rawMarkdown = contentGeneratorService.generateDocContent(request.getUserInput(), request.getDocType());
        return markdownPreviewParser.parseDocument(rawMarkdown, request.getDocType());
    }

    public PresentationPreviewResponse previewPresentation(PresentationPreviewRequest request) {
        String rawMarkdown = contentGeneratorService.generatePptContent(request.getUserInput(), request.getTopic());
        PresentationTheme theme = request.getTheme() == null || request.getTheme().isBlank()
                ? PresentationTheme.fromText(request.getUserInput() + "\n" + request.getTopic())
                : PresentationTheme.fromText(request.getTheme());
        List<PresentationSlide> slides = presentationDesignAdvisor.polish(presentationMarkdownParser.parse(rawMarkdown, request.getTopic()));
        List<String> warnings = slides.size() > 10
                ? List.of("当前预览包含 " + slides.size() + " 页，正式创建会逐页写入飞书演示文稿，耗时会随页数增加。")
                : List.of();
        int estimatedDurationMinutes = estimateDurationMinutes(slides);
        return PresentationPreviewResponse.builder()
                .artifactType("PRESENTATION")
                .title(resolvePreviewTitle(slides, request.getTopic()))
                .rawMarkdown(rawMarkdown)
                .generatedAt(Instant.now().toString())
                .theme(theme.getCode())
                .pageCount(slides.size())
                .estimatedDurationMinutes(estimatedDurationMinutes)
                .slides(slides.stream().map(this::toPreviewSlide).toList())
                .rehearsalTips(buildRehearsalTips(slides, estimatedDurationMinutes))
                .reviewChecklist(buildReviewChecklist(slides))
                .warnings(warnings)
                .build();
    }

    private String resolvePreviewTitle(List<PresentationSlide> slides, String fallbackTitle) {
        if (slides == null || slides.isEmpty() || slides.get(0).getTitle() == null || slides.get(0).getTitle().isBlank()) {
            return fallbackTitle;
        }
        return slides.get(0).getTitle();
    }

    private PresentationPreviewResponse.Slide toPreviewSlide(PresentationSlide slide) {
        List<String> bullets = slide.getBlocks().stream()
                .filter(block -> "bullets".equals(block.getType()))
                .flatMap(block -> block.getItems().stream())
                .toList();
        String bodyMarkdown = slide.getBlocks().stream()
                .map(block -> {
                    if ("paragraph".equals(block.getType())) {
                        return block.getText();
                    }
                    if ("bullets".equals(block.getType())) {
                        return String.join("\n", block.getItems().stream().map(item -> "- " + item).toList());
                    }
                    if ("table".equals(block.getType())) {
                        return String.join("\n", block.getRows().stream().map(row -> "| " + String.join(" | ", row) + " |").toList());
                    }
                    return "";
                })
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

        return PresentationPreviewResponse.Slide.builder()
                .id("slide-" + slide.getSlideNo())
                .slideNo(slide.getSlideNo())
                .title(slide.getTitle())
                .layout(slide.getLayout())
                .bodyMarkdown(bodyMarkdown)
                .bullets(bullets)
                .speakerNotes(buildSpeakerNotes(slide, bullets))
                .blocks(slide.getBlocks().stream().map(this::toPreviewBlock).toList())
                .build();
    }

    private String buildSpeakerNotes(PresentationSlide slide, List<String> bullets) {
        String title = slide.getTitle() == null || slide.getTitle().isBlank() ? "本页" : slide.getTitle();
        String layout = slide.getLayout() == null || slide.getLayout().isBlank() ? "content" : slide.getLayout();
        if ("cover".equals(layout)) {
            return "开场先说明汇报主题「" + title + "」，用一句话交代背景和听众收益，控制在 20 秒内。";
        }
        if ("two_column".equals(layout) && containsAny(title, "目录", "议程", "大纲")) {
            return "用这页快速建立听众预期，只点出汇报顺序，不展开细节，控制在 30 秒内。";
        }
        if ("timeline".equals(layout)) {
            return "讲解「" + title + "」时，按阶段说明先后顺序、关键动作和责任边界，最后强调最近一步要做什么。";
        }
        if ("comparison_table".equals(layout)) {
            return "讲解「" + title + "」时，先说明对比维度，再点出最需要决策或关注的差异，避免逐格朗读。";
        }
        if ("metric_cards".equals(layout)) {
            return "讲解「" + title + "」时，先给出最关键数字，再解释数字背后的业务含义和判断依据。";
        }
        if ("closing".equals(layout)) {
            return "收尾时先复述核心结论，再明确下一步动作和需要听众确认的事项，控制在 30 秒内。";
        }
        if (bullets != null && !bullets.isEmpty()) {
            String keyPoints = String.join("；", bullets.stream().limit(3).toList());
            return "讲解「" + title + "」时，先点明本页目的，再围绕 " + keyPoints
                    + " 展开说明。版式为 " + layout + "，建议控制在 45-60 秒。";
        }
        String text = slide.getBlocks().stream()
                .map(PresentationSlide.SlideBlock::getText)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("本页核心信息");
        return "讲解「" + title + "」时，先给出结论，再用「" + abbreviate(text, 36)
                + "」补充背景。版式为 " + layout + "，建议控制在 45 秒内。";
    }

    private int estimateDurationMinutes(List<PresentationSlide> slides) {
        int pageCount = slides == null ? 0 : slides.size();
        return Math.max(1, (int) Math.ceil(pageCount * 0.75));
    }

    private List<String> buildRehearsalTips(List<PresentationSlide> slides, int estimatedMinutes) {
        List<String> tips = new java.util.ArrayList<>();
        tips.add("建议总时长控制在约 " + estimatedMinutes + " 分钟，每页保留一个明确结论。");
        tips.add("正式汇报前先核对封面、核心结论、关键数据页、执行计划页和结尾页是否连贯。");
        tips.add("排练时优先讲清楚背景、目标、方案价值和下一步行动，避免逐字朗读页面内容。");
        if (slides != null && slides.stream().anyMatch(slide -> "comparison_table".equals(slide.getLayout()))) {
            tips.add("风险或对比页建议只讲最关键的 2-3 个差异，并给出明确应对动作。");
        }
        if (slides != null && slides.stream().anyMatch(slide -> "timeline".equals(slide.getLayout()))) {
            tips.add("时间线页需要明确最近一个节点的负责人、时间和验收标准。");
        }
        return tips;
    }

    private List<String> buildReviewChecklist(List<PresentationSlide> slides) {
        boolean hasTimeline = slides != null && slides.stream().anyMatch(slide -> "timeline".equals(slide.getLayout()));
        boolean hasMetrics = slides != null && slides.stream().anyMatch(slide -> "metric_cards".equals(slide.getLayout()));
        boolean hasRiskOrComparison = slides != null && slides.stream().anyMatch(slide -> "comparison_table".equals(slide.getLayout()));
        boolean hasClosing = slides != null && slides.stream().anyMatch(slide -> "closing".equals(slide.getLayout()));
        boolean recruiting = looksLikeRecruitingDeck(slides);
        List<String> checklist = new java.util.ArrayList<>();
        checklist.add("确认封面标题、汇报对象和场景一致。");
        if (recruiting) {
            checklist.add("确认候选人能看清岗位亮点、成长路径、投递动作和雇主价值。");
        }
        checklist.add(hasMetrics ? "核对关键数字、口径和来源，避免指标页空泛。" : "如涉及业务结果，建议补充关键数字或量化口径。");
        checklist.add(hasRiskOrComparison ? "检查风险、对比或应对动作是否具体到可执行层面。" : "如涉及方案取舍，建议补充风险、对比或应对动作。");
        checklist.add(hasTimeline ? "检查时间线节点是否能支撑行动计划。" : "如有项目推进内容，建议补充时间线或阶段安排。");
        checklist.add(hasClosing ? "确认结尾页包含明确下一步行动。" : "建议保留结尾页，收束结论和下一步行动。");
        return checklist;
    }

    private boolean looksLikeRecruitingDeck(List<PresentationSlide> slides) {
        if (slides == null || slides.isEmpty()) {
            return false;
        }
        String joined = slides.stream()
                .map(slide -> slide.getTitle() + " " + slide.getBlocks().stream()
                        .map(block -> (block.getText() == null ? "" : block.getText()) + " "
                                + (block.getItems() == null ? "" : String.join(" ", block.getItems())))
                        .reduce("", (left, right) -> left + " " + right))
                .reduce("", (left, right) -> left + " " + right);
        return containsAny(joined, "校招", "招聘", "宣讲", "岗位", "应聘", "简历", "投递", "实习", "转正", "毕业生");
    }

    private boolean containsAny(String text, String... keywords) {
        String value = text == null ? "" : text.toLowerCase();
        for (String keyword : keywords) {
            if (keyword != null && value.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private PresentationPreviewResponse.Block toPreviewBlock(PresentationSlide.SlideBlock block) {
        return PresentationPreviewResponse.Block.builder()
                .type(block.getType())
                .text(block.getText())
                .items(block.getItems())
                .rows(block.getRows())
                .build();
    }
}

