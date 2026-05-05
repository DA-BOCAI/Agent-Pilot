package com.hay.agent.service.presentation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresentationMarkdownParserTest {

    private final PresentationMarkdownParser parser = new PresentationMarkdownParser();

    @Test
    void parseShouldCleanMarkdownAndDetectTables() {
        List<PresentationSlide> slides = parser.parse("""
                # 双十一方案

                ## 项目目标
                - **GMV目标**：破3亿
                - `复购率` 提升15%

                ## 执行计划
                | 阶段 | 时间 |
                |------|------|
                | 预热 | 10.21 |
                """, "默认标题");

        assertEquals(2, slides.size());
        assertEquals("项目目标", slides.get(0).getTitle());
        assertEquals("GMV目标：破3亿", slides.get(0).getBlocks().get(0).getItems().get(0));
        assertEquals("复购率 提升15%", slides.get(0).getBlocks().get(0).getItems().get(1));
        assertEquals("table", slides.get(1).getBlocks().get(0).getType());
        assertEquals(List.of("阶段", "时间"), slides.get(1).getBlocks().get(0).getRows().get(0));
    }

    @Test
    void themeShouldInferFromUserText() {
        assertEquals(PresentationTheme.CAMPAIGN, PresentationTheme.fromText("双十一大促作战方案"));
        assertEquals(PresentationTheme.TECH, PresentationTheme.fromText("AI 平台技术架构"));
        assertEquals(PresentationTheme.MINIMAL, PresentationTheme.fromText("极简风格汇报"));
        assertEquals(PresentationTheme.BUSINESS, PresentationTheme.fromText("季度经营复盘"));
    }

    @Test
    void rendererShouldOutputValidSlideXmlWithThemeColors() {
        LarkSlideXmlRenderer renderer = new LarkSlideXmlRenderer();
        PresentationSlide slide = PresentationSlide.builder()
                .slideNo(2)
                .title("项目目标")
                .blocks(List.of(PresentationSlide.SlideBlock.builder()
                        .type("bullets")
                        .items(List.of("GMV破3亿", "复购率提升15%", "曝光量破10亿"))
                        .rows(List.of())
                        .build()))
                .build();

        String xml = renderer.render(slide, "双十一方案", PresentationTheme.CAMPAIGN);

        assertTrue(xml.startsWith("<slide xmlns=\"http://www.larkoffice.com/sml/2.0\">"));
        assertTrue(xml.contains(PresentationTheme.CAMPAIGN.getBackground()));
        assertTrue(xml.contains("rgba(255,255,255,0.16)"));
        assertTrue(xml.contains("topLeftY=\"190\""));
        assertTrue(xml.contains("3亿"));
        assertTrue(xml.contains("GMV破"));
        assertTrue(!xml.contains("<ul>"));
    }

    @Test
    void rendererShouldTurnNormalBulletsIntoInsightCards() {
        LarkSlideXmlRenderer renderer = new LarkSlideXmlRenderer();
        PresentationSlide slide = PresentationSlide.builder()
                .slideNo(2)
                .title("项目推进策略")
                .layout("content")
                .blocks(List.of(PresentationSlide.SlideBlock.builder()
                        .type("bullets")
                        .items(List.of("统一入口承接 IM 需求", "工作台负责预览精修", "飞书产物完成正式交付"))
                        .rows(List.of())
                        .build()))
                .build();

        String xml = renderer.render(slide, "Agent Copilot", PresentationTheme.BUSINESS);

        assertTrue(xml.contains("01"));
        assertTrue(xml.contains("02"));
        assertTrue(xml.contains("rgba(255,255,255,0.18)"));
        assertTrue(!xml.contains("<border color=\"rgba"));
        assertTrue(xml.contains("工作台负责预览精修"));
        assertTrue(!xml.contains("<ul>"));
    }

    @Test
    void rendererShouldUseTimelineForPhaseTables() {
        LarkSlideXmlRenderer renderer = new LarkSlideXmlRenderer();
        PresentationSlide slide = PresentationSlide.builder()
                .slideNo(3)
                .title("执行计划")
                .blocks(List.of(PresentationSlide.SlideBlock.builder()
                        .type("table")
                        .items(List.of())
                        .rows(List.of(
                                List.of("阶段", "时间", "动作"),
                                List.of("预热期", "10.21", "内容种草"),
                                List.of("爆发期", "11.11", "订单履约")
                        ))
                        .build()))
                .build();

        String xml = renderer.render(slide, "双十一方案", PresentationTheme.CAMPAIGN);

        assertTrue(xml.contains("预热期"));
        assertTrue(xml.contains("内容种草"));
        assertTrue(xml.contains("<line startX=\"114\""));
    }

    @Test
    void advisorShouldAssignLayoutAndCompactLongBullets() {
        PresentationDesignAdvisor advisor = new PresentationDesignAdvisor();
        List<PresentationSlide> slides = advisor.polish(List.of(
                PresentationSlide.builder()
                        .slideNo(1)
                        .title("演示方案")
                        .blocks(List.of())
                        .build(),
                PresentationSlide.builder()
                        .slideNo(2)
                        .title("关键动作")
                        .blocks(List.of(PresentationSlide.SlideBlock.builder()
                                .type("bullets")
                                .items(List.of(
                                        "搭建端到端演示链路，覆盖IM触发、双确认、工作台精修和飞书交付",
                                        "明确前端首屏快照加载策略",
                                        "补齐Doc到PPT组合编排",
                                        "优化演示用例",
                                        "完善异常处理"))
                                .rows(List.of())
                                .build()))
                        .build()));

        assertEquals("two_column", slides.get(1).getLayout());
        assertTrue(slides.get(1).getBlocks().get(0).getItems().get(0).length() <= 34);
    }

    @Test
    void advisorShouldAddAgendaAndClosingForLongDecks() {
        PresentationDesignAdvisor advisor = new PresentationDesignAdvisor();
        List<PresentationSlide> slides = advisor.polish(List.of(
                simpleSlide(1, "项目复盘"),
                simpleSlide(2, "背景与目标"),
                simpleSlide(3, "核心进展"),
                simpleSlide(4, "风险与计划")
        ));

        assertEquals("目录", slides.get(1).getTitle());
        assertEquals("核心结论", slides.get(2).getTitle());
        assertEquals("下一步行动计划", slides.get(slides.size() - 2).getTitle());
        assertEquals("closing", slides.get(slides.size() - 1).getLayout());
    }

    @Test
    void advisorShouldTurnRiskBulletsIntoRiskTable() {
        PresentationDesignAdvisor advisor = new PresentationDesignAdvisor();
        List<PresentationSlide> slides = advisor.polish(List.of(
                simpleSlide(1, "项目复盘"),
                PresentationSlide.builder()
                        .slideNo(2)
                        .title("风险与应对")
                        .blocks(List.of(PresentationSlide.SlideBlock.builder()
                                .type("bullets")
                                .items(List.of("排期延期风险", "数据口径不一致", "跨团队资源不足"))
                                .rows(List.of())
                                .build()))
                        .build()
        ));

        PresentationSlide riskSlide = slides.get(1);
        assertEquals("comparison_table", riskSlide.getLayout());
        assertEquals("table", riskSlide.getBlocks().get(0).getType());
        assertEquals(List.of("风险项", "影响", "应对"), riskSlide.getBlocks().get(0).getRows().get(0));
        assertTrue(riskSlide.getBlocks().get(0).getRows().get(1).contains("影响交付节奏"));
    }

    @Test
    void advisorShouldTurnPlanBulletsIntoTimelineTable() {
        PresentationDesignAdvisor advisor = new PresentationDesignAdvisor();
        List<PresentationSlide> slides = advisor.polish(List.of(
                simpleSlide(1, "项目复盘"),
                PresentationSlide.builder()
                        .slideNo(2)
                        .title("推进计划")
                        .blocks(List.of(PresentationSlide.SlideBlock.builder()
                                .type("bullets")
                                .items(List.of("确认汇报口径", "执行材料精修", "复盘交付反馈"))
                                .rows(List.of())
                                .build()))
                        .build()
        ));

        PresentationSlide planSlide = slides.get(1);
        assertEquals("timeline", planSlide.getLayout());
        assertEquals("table", planSlide.getBlocks().get(0).getType());
        assertEquals(List.of("阶段", "时间", "动作"), planSlide.getBlocks().get(0).getRows().get(0));
        assertEquals("阶段 1", planSlide.getBlocks().get(0).getRows().get(1).get(0));
    }

    @Test
    void advisorShouldExpandSparseDeckIntoReportStructure() {
        PresentationDesignAdvisor advisor = new PresentationDesignAdvisor();
        List<PresentationSlide> slides = advisor.polish(List.of(
                PresentationSlide.builder()
                        .slideNo(1)
                        .title("AI 项目汇报")
                        .blocks(List.of(PresentationSlide.SlideBlock.builder()
                                .type("bullets")
                                .items(List.of("说明项目背景"))
                                .rows(List.of())
                                .build()))
                        .build()
        ));

        assertTrue(slides.size() >= 7);
        assertEquals("cover", slides.get(0).getLayout());
        assertTrue(slides.stream().anyMatch(slide -> "核心结论".equals(slide.getTitle())));
        assertTrue(slides.stream().anyMatch(slide -> "timeline".equals(slide.getLayout())));
        assertTrue(slides.stream().anyMatch(slide -> "comparison_table".equals(slide.getLayout())));
        assertEquals("closing", slides.get(slides.size() - 1).getLayout());
    }

    @Test
    void advisorShouldRemoveDecorativeIconsFromTitlesAndBullets() {
        PresentationDesignAdvisor advisor = new PresentationDesignAdvisor();
        List<PresentationSlide> slides = advisor.polish(List.of(
                PresentationSlide.builder()
                        .slideNo(1)
                        .title("🚀 项目封面")
                        .blocks(List.of(PresentationSlide.SlideBlock.builder()
                                .type("bullets")
                                .items(List.of("✅ 目标对齐", "📌 关键动作"))
                                .rows(List.of())
                                .build()))
                        .build(),
                simpleSlide(2, "总结")
        ));

        assertEquals("项目封面", slides.get(0).getTitle());
        List<String> bullets = slides.get(0).getBlocks().get(0).getItems();
        assertEquals("目标对齐", bullets.get(0));
        assertEquals("关键动作", bullets.get(1));
    }

    @Test
    void rendererShouldUseComparisonTableLayout() {
        LarkSlideXmlRenderer renderer = new LarkSlideXmlRenderer();
        PresentationSlide slide = PresentationSlide.builder()
                .slideNo(4)
                .title("方案对比")
                .layout("comparison_table")
                .blocks(List.of(PresentationSlide.SlideBlock.builder()
                        .type("table")
                        .rows(List.of(
                                List.of("维度", "现状", "目标"),
                                List.of("体验", "卡片割裂", "工作台统一承接"),
                                List.of("交付", "链接分散", "交付包汇总")
                        ))
                        .items(List.of())
                        .build()))
                .build();

        String xml = renderer.render(slide, "IM Agent方案", PresentationTheme.BUSINESS);

        assertTrue(xml.contains("方案对比"));
        assertTrue(xml.contains("工作台统一承接"));
        assertTrue(xml.contains("width=\"266\""));
    }

    @Test
    void rendererShouldUseEditorialAgendaLayout() {
        LarkSlideXmlRenderer renderer = new LarkSlideXmlRenderer();
        PresentationSlide slide = PresentationSlide.builder()
                .slideNo(2)
                .title("目录")
                .layout("two_column")
                .blocks(List.of(PresentationSlide.SlideBlock.builder()
                        .type("bullets")
                        .items(List.of("背景与目标", "核心结论", "方案展开", "推进计划"))
                        .rows(List.of())
                        .build()))
                .build();

        String xml = renderer.render(slide, "项目汇报", PresentationTheme.BUSINESS);

        assertTrue(!xml.contains("PRESENTATION"));
        assertTrue(xml.contains("fontSize=\"23\""));
        assertTrue(xml.contains("01"));
        assertTrue(xml.contains("topLeftX=\"506\""));
        assertTrue(!xml.contains("<ul>"));
    }

    @Test
    void rendererShouldUseExecutiveSummaryLayoutForCoreConclusion() {
        LarkSlideXmlRenderer renderer = new LarkSlideXmlRenderer();
        PresentationSlide slide = PresentationSlide.builder()
                .slideNo(3)
                .title("核心结论")
                .layout("content")
                .blocks(List.of(PresentationSlide.SlideBlock.builder()
                        .type("bullets")
                        .items(List.of("IM 到交付链路已经具备稳定演示条件", "PPT 视觉模板需要继续精修", "Doc 到 PPT 编排适合作为加分项"))
                        .rows(List.of())
                        .build()))
                .build();

        String xml = renderer.render(slide, "项目汇报", PresentationTheme.BUSINESS);

        assertTrue(xml.contains("fontSize=\"25\""));
        assertTrue(xml.contains("topLeftY=\"288\""));
        assertTrue(xml.contains("IM 到交付链路"));
        assertTrue(!xml.contains("rgba(255,255,255,0.18)"));
    }

    private PresentationSlide simpleSlide(int slideNo, String title) {
        return PresentationSlide.builder()
                .slideNo(slideNo)
                .title(title)
                .blocks(List.of(PresentationSlide.SlideBlock.builder()
                        .type("bullets")
                        .items(List.of("关键结论", "下一步安排"))
                        .rows(List.of())
                        .build()))
                .build();
    }
}
