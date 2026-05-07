package com.hay.agent.service.presentation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class PresentationDesignAdvisor {

    private static final Pattern METRIC_PATTERN = Pattern.compile(".*(\\d|%|万|亿|w|W|TOP|top).*");
    private static final Pattern DECORATIVE_PREFIX = Pattern.compile("^[\\p{So}\\p{Sk}\\u2600-\\u27BF\\uD83C\\uDC00-\\uD83D\\uDFFF\\s]+");
    private static final int MIN_REPORT_SLIDES = 7;
    private static final int MAX_BULLETS_PER_BLOCK = 6;
    private static final int MAX_BULLET_CHARS = 42;
    private static final int SPLIT_BULLET_THRESHOLD = 6;
    private static final int MAX_SAFE_BULLETS_PER_SLIDE = 4;
    private static final int MAX_SAFE_BULLET_CHARS_PER_SLIDE = 150;

    public List<PresentationSlide> polish(List<PresentationSlide> slides) {
        if (slides == null || slides.isEmpty()) {
            return List.of();
        }

        List<PresentationSlide> normalized = normalizeSlides(slides);
        List<PresentationSlide> structuredSlides = ensureNarrativeStructure(normalized);
        structuredSlides = splitDensitySafeSlides(structuredSlides);
        List<PresentationSlide> polished = new ArrayList<>();
        for (int i = 0; i < structuredSlides.size(); i++) {
            PresentationSlide slide = structuredSlides.get(i);
            PresentationLayout layout = resolveLayout(slide, i, structuredSlides.size());
            polished.add(PresentationSlide.builder()
                    .slideNo(i + 1)
                    .title(cleanText(slide.getTitle(), "第 " + (i + 1) + " 页"))
                    .layout(layout.getCode())
                    .blocks(polishBlocks(slide.getBlocks(), layout))
                    .build());
        }
        return polished;
    }

    private List<PresentationSlide> normalizeSlides(List<PresentationSlide> slides) {
        List<PresentationSlide> normalized = new ArrayList<>();
        for (int i = 0; i < slides.size(); i++) {
            PresentationSlide slide = slides.get(i);
            if (slide == null) {
                continue;
            }
            normalized.add(PresentationSlide.builder()
                    .slideNo(i + 1)
                    .title(cleanText(slide.getTitle(), "第 " + (i + 1) + " 页"))
                    .layout(slide.getLayout())
                    .blocks(normalizeBlocks(slide.getBlocks()))
                    .build());
        }
        return normalized;
    }

    private List<PresentationSlide.SlideBlock> normalizeBlocks(List<PresentationSlide.SlideBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        List<PresentationSlide.SlideBlock> normalized = new ArrayList<>();
        for (PresentationSlide.SlideBlock block : blocks) {
            if (block == null) {
                continue;
            }
            normalized.add(PresentationSlide.SlideBlock.builder()
                    .type(block.getType())
                    .text(block.getText() == null ? null : cleanText(block.getText(), ""))
                    .items(block.getItems() == null ? List.of() : block.getItems().stream()
                            .map(item -> cleanText(item, ""))
                            .filter(item -> !item.isBlank())
                            .toList())
                    .rows(cleanRows(block.getRows()))
                    .build());
        }
        return normalized;
    }

    private List<List<String>> cleanRows(List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<List<String>> cleaned = new ArrayList<>();
        for (List<String> row : rows) {
            if (row == null) {
                continue;
            }
            cleaned.add(row.stream().map(cell -> cleanText(cell, "")).toList());
        }
        return cleaned;
    }

    private List<PresentationSlide> ensureNarrativeStructure(List<PresentationSlide> slides) {
        List<PresentationSlide> result = expandSparseDeck(slides);
        result = ensureRecruitingStructure(result);
        if (result.size() >= 4 && !hasTitleLike(result, "目录", "议程", "大纲")) {
            result.add(Math.min(1, result.size()), agendaSlide(result));
        }
        if (result.size() >= 5 && !hasTitleLike(result, "核心结论", "摘要", "概览", "总览")) {
            int insertIndex = hasTitleLike(result, "目录", "议程", "大纲") ? Math.min(2, result.size()) : Math.min(1, result.size());
            result.add(insertIndex, executiveSummarySlide(result));
        }
        if (needsActionPlan(result) && !hasTitleLike(result, "下一步", "行动计划", "推进计划", "落地计划")) {
            int insertIndex = hasClosing(result) ? Math.max(0, result.size() - 1) : result.size();
            result.add(insertIndex, actionPlanSlide());
        }
        if (result.size() >= 4 && !hasClosing(result)) {
            result.add(closingSlide(result));
        }
        return result;
    }

    private List<PresentationSlide> splitDensitySafeSlides(List<PresentationSlide> slides) {
        if (slides == null || slides.isEmpty()) {
            return List.of();
        }
        List<PresentationSlide> result = new ArrayList<>();
        for (int i = 0; i < slides.size(); i++) {
            PresentationSlide slide = slides.get(i);
            if (!shouldSplitSlide(slide, i, slides.size())) {
                result.add(slide);
                continue;
            }
            List<List<String>> chunks = chunkBullets(allBullets(slide));
            for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
                result.add(PresentationSlide.builder()
                        .title(chunkIndex == 0 ? slide.getTitle() : continuationTitle(slide.getTitle()))
                        .layout(PresentationLayout.CONTENT.getCode())
                        .blocks(List.of(PresentationSlide.SlideBlock.builder()
                                .type("bullets")
                                .items(chunks.get(chunkIndex))
                                .rows(List.of())
                                .build()))
                        .build());
            }
        }
        return result;
    }

    private boolean shouldSplitSlide(PresentationSlide slide, int index, int totalSlides) {
        if (slide == null || slide.getBlocks() == null || slide.getBlocks().isEmpty()) {
            return false;
        }
        if (index == 0 || index == totalSlides - 1 || hasAnyTable(slide)) {
            return false;
        }
        String title = slide.getTitle() == null ? "" : slide.getTitle();
        if (containsAny(title, "鐩綍", "璁▼", "澶х翰", "agenda", "contents")) {
            return false;
        }
        PresentationLayout explicit = PresentationLayout.fromCode(slide.getLayout());
        if (explicit == PresentationLayout.COVER
                || explicit == PresentationLayout.CLOSING
                || explicit == PresentationLayout.TIMELINE
                || explicit == PresentationLayout.COMPARISON_TABLE
                || explicit == PresentationLayout.SECTION_DIVIDER) {
            return false;
        }
        List<String> bullets = allBullets(slide);
        if (bullets.size() <= SPLIT_BULLET_THRESHOLD) {
            return false;
        }
        return slide.getBlocks().stream()
                .allMatch(block -> "bullets".equals(block.getType())
                        || block.getText() == null
                        || block.getText().isBlank());
    }

    private List<List<String>> chunkBullets(List<String> bullets) {
        List<List<String>> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentChars = 0;
        for (String bullet : bullets) {
            String item = compactText(bullet);
            if (!current.isEmpty()
                    && (current.size() >= MAX_SAFE_BULLETS_PER_SLIDE
                    || currentChars + item.length() > MAX_SAFE_BULLET_CHARS_PER_SLIDE)) {
                chunks.add(current);
                current = new ArrayList<>();
                currentChars = 0;
            }
            current.add(item);
            currentChars += item.length();
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }

    private boolean hasAnyTable(PresentationSlide slide) {
        return slide.getBlocks().stream()
                .anyMatch(block -> "table".equals(block.getType()) && block.getRows() != null && !block.getRows().isEmpty());
    }

    private String continuationTitle(String title) {
        String base = cleanText(title, "More details");
        return base.endsWith("(cont.)") ? base : base + " (cont.)";
    }

    private List<PresentationSlide> ensureRecruitingStructure(List<PresentationSlide> slides) {
        if (!looksLikeRecruitingDeck(slides)) {
            return slides;
        }
        List<PresentationSlide> result = new ArrayList<>(slides);
        int insertIndex = Math.min(3, Math.max(1, result.size()));
        if (!hasTitleLike(result, "为什么加入", "岗位亮点", "选择我们", "雇主")) {
            result.add(insertIndex++, simpleBullets("为什么值得加入", List.of(
                    "真实业务场景：从校招第一天接触可落地的项目与客户问题",
                    "成长路径清晰：导师带教、阶段复盘和岗位能力模型同步推进",
                    "本地支持稳定：校企合作、实习转正和长期发展路径更容易衔接"
            )));
        }
        if (!hasTitleLike(result, "成长路径", "培养机制", "投递", "校招流程")) {
            result.add(Math.min(insertIndex, result.size()), simpleTable("成长路径与投递行动", List.of(
                    List.of("阶段", "时间", "行动"),
                    List.of("了解岗位", "宣讲现场", "关注岗位职责、培养机制和业务方向"),
                    List.of("投递沟通", "宣讲后", "扫码投递并补充项目经历、实习经历或作品"),
                    List.of("面试准备", "后续通知", "围绕岗位能力准备案例，主动说明个人优势")
            )));
        }
        return result;
    }

    private List<PresentationSlide> expandSparseDeck(List<PresentationSlide> slides) {
        if (slides.size() >= MIN_REPORT_SLIDES || totalSignalCount(slides) >= 10) {
            return new ArrayList<>(slides);
        }
        if (slides.size() > 1 && slides.stream().anyMatch(slide ->
                looksLikePlanSlide(slide) || looksLikeRiskSlide(slide) || bulletCount(slide) >= 5)) {
            return new ArrayList<>(slides);
        }

        String title = slides.get(0).getTitle() == null || slides.get(0).getTitle().isBlank()
                ? "演示文稿"
                : slides.get(0).getTitle();
        List<String> signals = slides.stream()
                .flatMap(slide -> allBullets(slide).stream())
                .filter(item -> item != null && !item.isBlank())
                .limit(8)
                .toList();
        String keySignal = signals.isEmpty() ? "围绕目标、方案、执行和交付形成完整汇报闭环" : signals.get(0);

        List<PresentationSlide> expanded = new ArrayList<>();
        expanded.add(slides.get(0));
        expanded.add(simpleBullets("目标与受众", List.of(
                "明确本次汇报要回答的问题和预期决策",
                "对齐听众关注点、交付物范围和成功标准",
                compactText(keySignal)
        )));
        expanded.add(simpleBullets("核心判断", defaultConclusions(slides)));
        expanded.add(simpleBullets("方案展开", List.of(
                "把核心内容拆成可理解、可比较、可执行的模块",
                "保留关键依据，避免只呈现空泛结论",
                "用结构化页面承接后续精修和正式交付"
        )));
        expanded.add(simpleTable("推进路径", List.of(
                List.of("阶段", "时间", "动作"),
                List.of("确认口径", "近期", "补齐目标、对象、数据来源和汇报范围"),
                List.of("内容精修", "执行期", "完善页面结构、讲稿和关键图表"),
                List.of("交付复盘", "交付后", "收集反馈并沉淀为可复用模板")
        )));
        expanded.add(simpleTable("风险与应对", List.of(
                List.of("风险项", "影响", "应对"),
                List.of("信息不足", "页面容易空泛", "在确认阶段补齐背景、对象和指标"),
                List.of("内容堆叠", "不像 PPT", "拆成结论页、对比页和行动页"),
                List.of("交付口径不清", "影响验收", "在最后一页明确下一步动作")
        )));
        if (slides.size() > 1) {
            expanded.addAll(slides.subList(1, slides.size()));
        }
        return expanded;
    }

    private int totalSignalCount(List<PresentationSlide> slides) {
        int count = 0;
        for (PresentationSlide slide : slides) {
            count += allBullets(slide).size();
            count += slide.getBlocks() == null ? 0 : slide.getBlocks().stream()
                    .filter(block -> block.getText() != null && !block.getText().isBlank())
                    .count();
        }
        return count;
    }

    private List<String> defaultConclusions(List<PresentationSlide> slides) {
        List<String> conclusions = slides.stream()
                .filter(slide -> slide != null && slide.getTitle() != null)
                .map(this::summarizeSlideAsConclusion)
                .filter(item -> item != null && !item.isBlank())
                .limit(4)
                .toList();
        if (!conclusions.isEmpty()) {
            return conclusions;
        }
        return List.of(
                "主题与交付方向已经明确",
                "后续重点是补充证据、结构和行动安排",
                "正式 PPT 需要同时支持预览、讲稿和交付复盘"
        );
    }

    private PresentationSlide simpleBullets(String title, List<String> items) {
        return PresentationSlide.builder()
                .title(title)
                .blocks(List.of(PresentationSlide.SlideBlock.builder()
                        .type("bullets")
                        .items(items)
                        .rows(List.of())
                        .build()))
                .build();
    }

    private PresentationSlide simpleTable(String title, List<List<String>> rows) {
        return PresentationSlide.builder()
                .title(title)
                .blocks(List.of(PresentationSlide.SlideBlock.builder()
                        .type("table")
                        .items(List.of())
                        .rows(rows)
                        .build()))
                .build();
    }

    private PresentationSlide agendaSlide(List<PresentationSlide> slides) {
        List<String> titles = slides.stream()
                .map(PresentationSlide::getTitle)
                .filter(title -> title != null && !title.isBlank())
                .filter(title -> !containsAny(title, "封面", "总结", "致谢", "目录", "议程", "大纲"))
                .limit(6)
                .toList();
        return simpleBullets("目录", titles.isEmpty()
                ? List.of("背景与目标", "核心方案", "执行计划", "总结与下一步")
                : titles);
    }

    private PresentationSlide executiveSummarySlide(List<PresentationSlide> slides) {
        List<String> conclusions = slides.stream()
                .filter(slide -> slide != null && slide.getTitle() != null)
                .filter(slide -> !containsAny(slide.getTitle(), "封面", "目录", "议程", "大纲", "致谢", "总结"))
                .map(this::summarizeSlideAsConclusion)
                .filter(item -> item != null && !item.isBlank())
                .limit(4)
                .toList();
        return simpleBullets("核心结论", conclusions.isEmpty()
                ? List.of("目标与背景已经明确", "方案路径具备可执行性", "风险与下一步需要持续跟进")
                : conclusions);
    }

    private String summarizeSlideAsConclusion(PresentationSlide slide) {
        String title = cleanText(slide.getTitle(), "本页");
        List<String> bullets = allBullets(slide).stream().limit(1).toList();
        if (!bullets.isEmpty()) {
            return compactText(title + "：" + bullets.get(0));
        }
        String text = blockText(slide.getBlocks()).replaceAll("\\s+", " ").trim();
        if (!text.isBlank()) {
            return compactText(title + "：" + text);
        }
        return title;
    }

    private PresentationSlide actionPlanSlide() {
        return simpleTable("下一步行动计划", List.of(
                List.of("阶段", "时间", "动作"),
                List.of("确认口径", "近期", "核对目标、对象、关键数据与汇报范围"),
                List.of("推进落地", "执行期", "按优先级拆解责任人与里程碑"),
                List.of("复盘优化", "交付后", "收集反馈并更新文档与演示稿")
        ));
    }

    private PresentationSlide closingSlide(List<PresentationSlide> slides) {
        String conclusion = slides.stream()
                .filter(slide -> slide != null && !hasClosing(List.of(slide)))
                .map(this::summarizeSlideAsConclusion)
                .filter(item -> item != null && !item.isBlank())
                .findFirst()
                .orElse("聚焦关键结论，确认后续行动，并沉淀为可复用的团队资产。");
        return PresentationSlide.builder()
                .title("总结与下一步")
                .layout(PresentationLayout.CLOSING.getCode())
                .blocks(List.of(PresentationSlide.SlideBlock.builder()
                        .type("paragraph")
                        .text(compactText(conclusion))
                        .items(List.of())
                        .rows(List.of())
                        .build()))
                .build();
    }

    private boolean needsActionPlan(List<PresentationSlide> slides) {
        String joined = slides.stream()
                .map(slide -> (slide.getTitle() == null ? "" : slide.getTitle()) + " " + blockText(slide.getBlocks()))
                .reduce("", (left, right) -> left + " " + right);
        return containsAny(joined, "项目", "推进", "计划", "落地", "风险", "复盘", "方案", "策略");
    }

    private boolean looksLikeRecruitingDeck(List<PresentationSlide> slides) {
        String joined = slides.stream()
                .map(slide -> (slide.getTitle() == null ? "" : slide.getTitle()) + " " + blockText(slide.getBlocks()))
                .reduce("", (left, right) -> left + " " + right);
        return containsAny(joined, "校招", "招聘", "宣讲", "岗位", "应聘", "简历", "投递", "实习", "转正", "毕业生");
    }

    private boolean hasClosing(List<PresentationSlide> slides) {
        return hasTitleLike(slides, "总结", "致谢", "结尾", "thanks");
    }

    private boolean hasTitleLike(List<PresentationSlide> slides, String... keywords) {
        return slides.stream()
                .map(PresentationSlide::getTitle)
                .filter(title -> title != null)
                .anyMatch(title -> containsAny(title.toLowerCase(), keywords));
    }

    private String blockText(List<PresentationSlide.SlideBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (PresentationSlide.SlideBlock block : blocks) {
            if (block.getText() != null) {
                builder.append(block.getText()).append(' ');
            }
            if (block.getItems() != null) {
                builder.append(String.join(" ", block.getItems())).append(' ');
            }
        }
        return builder.toString();
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

    public PresentationLayout resolveLayout(PresentationSlide slide, int index, int totalSlides) {
        String title = slide == null || slide.getTitle() == null ? "" : slide.getTitle();
        PresentationLayout explicit = PresentationLayout.fromCode(slide == null ? null : slide.getLayout());
        if (explicit != null) {
            return explicit;
        }
        if (index == 0 || title.contains("封面")) {
            return PresentationLayout.COVER;
        }
        if (index == totalSlides - 1 && (title.contains("总结") || title.contains("致谢") || title.toLowerCase().contains("thanks"))) {
            return PresentationLayout.CLOSING;
        }
        if (title.contains("目录") || title.contains("议程") || title.contains("大纲")) {
            return PresentationLayout.TWO_COLUMN;
        }
        if (looksLikeSectionDivider(slide)) {
            return PresentationLayout.SECTION_DIVIDER;
        }
        if (looksLikeRiskSlide(slide)) {
            return PresentationLayout.COMPARISON_TABLE;
        }
        if (looksLikePlanSlide(slide)) {
            return PresentationLayout.TIMELINE;
        }
        if (hasTimelineTable(slide)) {
            return PresentationLayout.TIMELINE;
        }
        if (hasComparisonTable(slide)) {
            return PresentationLayout.COMPARISON_TABLE;
        }
        if (hasMetricBullets(slide)) {
            return PresentationLayout.METRIC_CARDS;
        }
        if (bulletCount(slide) >= 5) {
            return PresentationLayout.TWO_COLUMN;
        }
        return PresentationLayout.CONTENT;
    }

    private List<PresentationSlide.SlideBlock> polishBlocks(List<PresentationSlide.SlideBlock> blocks, PresentationLayout layout) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        if (layout == PresentationLayout.TIMELINE && firstTable(blocks).isEmpty()) {
            List<String> bullets = allBullets(blocks);
            if (!bullets.isEmpty()) {
                return List.of(toTimelineTable(bullets));
            }
        }
        if (layout == PresentationLayout.COMPARISON_TABLE && firstTable(blocks).isEmpty()) {
            List<String> bullets = allBullets(blocks);
            if (!bullets.isEmpty()) {
                return List.of(toRiskTable(bullets));
            }
        }
        List<PresentationSlide.SlideBlock> polished = new ArrayList<>();
        for (PresentationSlide.SlideBlock block : blocks) {
            if (!"bullets".equals(block.getType()) || block.getItems() == null) {
                polished.add(block);
                continue;
            }
            List<String> items = block.getItems().stream()
                    .filter(item -> item != null && !item.isBlank())
                    .map(this::compactText)
                    .limit(layout == PresentationLayout.TWO_COLUMN ? 8 : MAX_BULLETS_PER_BLOCK)
                    .toList();
            polished.add(PresentationSlide.SlideBlock.builder()
                    .type(block.getType())
                    .text(block.getText())
                    .items(items)
                    .rows(block.getRows())
                    .build());
        }
        return polished;
    }

    private java.util.Optional<PresentationSlide.SlideBlock> firstTable(List<PresentationSlide.SlideBlock> blocks) {
        if (blocks == null) {
            return java.util.Optional.empty();
        }
        return blocks.stream()
                .filter(block -> "table".equals(block.getType()) && block.getRows() != null && !block.getRows().isEmpty())
                .findFirst();
    }

    private PresentationSlide.SlideBlock toTimelineTable(List<String> bullets) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("阶段", "时间", "动作"));
        for (int i = 0; i < Math.min(4, bullets.size()); i++) {
            rows.add(List.of("阶段 " + (i + 1), resolveTimeHint(bullets.get(i), i), compactText(bullets.get(i))));
        }
        return PresentationSlide.SlideBlock.builder()
                .type("table")
                .items(List.of())
                .rows(rows)
                .build();
    }

    private String resolveTimeHint(String text, int index) {
        String value = text == null ? "" : text;
        if (containsAny(value, "近期", "本周", "本月", "Q1", "Q2", "Q3", "Q4", "第一", "第二", "第三")) {
            return compactText(value.replaceAll(".*?(近期|本周|本月|Q[1-4]|第一阶段|第二阶段|第三阶段).*", "$1"));
        }
        return switch (index) {
            case 0 -> "近期";
            case 1 -> "执行期";
            case 2 -> "复盘期";
            default -> "持续";
        };
    }

    private PresentationSlide.SlideBlock toRiskTable(List<String> bullets) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("风险项", "影响", "应对"));
        for (String bullet : bullets.stream().limit(4).toList()) {
            rows.add(List.of(compactText(bullet), inferRiskImpact(bullet), inferRiskResponse(bullet)));
        }
        return PresentationSlide.SlideBlock.builder()
                .type("table")
                .items(List.of())
                .rows(rows)
                .build();
    }

    private String inferRiskImpact(String text) {
        if (containsAny(text, "延期", "时间", "排期")) {
            return "影响交付节奏";
        }
        if (containsAny(text, "数据", "口径", "指标")) {
            return "影响汇报可信度";
        }
        if (containsAny(text, "资源", "人力", "协同")) {
            return "影响执行效率";
        }
        return "影响目标达成";
    }

    private String inferRiskResponse(String text) {
        if (containsAny(text, "延期", "时间", "排期")) {
            return "拆解里程碑并提前预警";
        }
        if (containsAny(text, "数据", "口径", "指标")) {
            return "统一口径并补充来源";
        }
        if (containsAny(text, "资源", "人力", "协同")) {
            return "明确责任人与协作机制";
        }
        return "制定跟进动作与负责人";
    }

    private String cleanText(String text, String fallback) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        value = DECORATIVE_PREFIX.matcher(value).replaceFirst("").trim();
        value = value.replaceAll("^[✅✔☑•●■◆◇▶▷►▸▪▫\\-—_\\s]+", "").trim();
        value = value.replaceAll("\\s*[✅✔☑]$", "").trim();
        return value.isBlank() ? fallback : value;
    }

    private String compactText(String text) {
        String value = cleanText(text, "").replaceAll("\\s+", " ").trim();
        if (value.length() <= MAX_BULLET_CHARS) {
            return value;
        }
        int split = Math.max(value.lastIndexOf('；', MAX_BULLET_CHARS), value.lastIndexOf('，', MAX_BULLET_CHARS));
        if (split >= 12) {
            return value.substring(0, split).trim();
        }
        return value.substring(0, MAX_BULLET_CHARS - 1).trim() + "...";
    }

    private boolean hasMetricBullets(PresentationSlide slide) {
        if (slide == null || slide.getBlocks() == null) {
            return false;
        }
        for (PresentationSlide.SlideBlock block : slide.getBlocks()) {
            if (!"bullets".equals(block.getType()) || block.getItems() == null || block.getItems().size() < 2) {
                continue;
            }
            long metricCount = block.getItems().stream().filter(item -> METRIC_PATTERN.matcher(item).matches()).count();
            if (metricCount >= 2) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeRiskSlide(PresentationSlide slide) {
        if (slide == null) {
            return false;
        }
        String title = slide.getTitle() == null ? "" : slide.getTitle();
        return containsAny(title, "风险", "问题", "挑战", "阻塞", "应对")
                && !hasComparisonTable(slide)
                && bulletCount(slide) >= 2;
    }

    private boolean looksLikePlanSlide(PresentationSlide slide) {
        if (slide == null) {
            return false;
        }
        String title = slide.getTitle() == null ? "" : slide.getTitle();
        return containsAny(title, "计划", "路径", "里程碑", "下一步", "推进", "行动")
                && !hasTimelineTable(slide)
                && bulletCount(slide) >= 2;
    }

    private boolean looksLikeSectionDivider(PresentationSlide slide) {
        if (slide == null || slide.getBlocks() == null || slide.getBlocks().isEmpty()) {
            return true;
        }
        if (hasTimelineTable(slide) || hasComparisonTable(slide) || hasMetricBullets(slide)) {
            return false;
        }
        long textBlocks = slide.getBlocks().stream()
                .filter(block -> block.getText() != null && !block.getText().isBlank())
                .count();
        return bulletCount(slide) <= 1 && textBlocks <= 1 && cleanText(slide.getTitle(), "").length() <= 18;
    }

    private boolean hasTimelineTable(PresentationSlide slide) {
        return firstTableHeader(slide).map(header -> header.contains("阶段")
                || header.contains("时间")
                || header.toLowerCase().contains("phase")).orElse(false);
    }

    private boolean hasComparisonTable(PresentationSlide slide) {
        return firstTableHeader(slide).map(header -> header.contains("维度")
                || header.contains("对比")
                || header.contains("现状")
                || header.contains("目标")
                || header.contains("风险")
                || header.contains("影响")
                || header.contains("应对")
                || header.toLowerCase().contains("compare")).orElse(false);
    }

    private java.util.Optional<String> firstTableHeader(PresentationSlide slide) {
        if (slide == null || slide.getBlocks() == null) {
            return java.util.Optional.empty();
        }
        return slide.getBlocks().stream()
                .filter(block -> "table".equals(block.getType()) && block.getRows() != null && !block.getRows().isEmpty())
                .map(block -> String.join(" ", block.getRows().get(0)))
                .findFirst();
    }

    private int bulletCount(PresentationSlide slide) {
        if (slide == null || slide.getBlocks() == null) {
            return 0;
        }
        return slide.getBlocks().stream()
                .filter(block -> "bullets".equals(block.getType()) && block.getItems() != null)
                .mapToInt(block -> block.getItems().size())
                .sum();
    }

    private List<String> allBullets(PresentationSlide slide) {
        return slide == null ? List.of() : allBullets(slide.getBlocks());
    }

    private List<String> allBullets(List<PresentationSlide.SlideBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        List<String> bullets = new ArrayList<>();
        for (PresentationSlide.SlideBlock block : blocks) {
            if ("bullets".equals(block.getType()) && block.getItems() != null) {
                block.getItems().stream()
                        .filter(item -> item != null && !item.isBlank())
                        .map(item -> cleanText(item, ""))
                        .filter(item -> !item.isBlank())
                        .forEach(bullets::add);
            }
        }
        return bullets;
    }
}
