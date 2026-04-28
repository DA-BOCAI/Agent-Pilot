package com.hay.agent.service;

import com.hay.agent.api.dto.preview.DocumentPreviewResponse;
import com.hay.agent.api.dto.preview.PresentationPreviewResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class MarkdownPreviewParser {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern BULLET_PATTERN = Pattern.compile("^(?:[-*+]\\s+|\\d+[.)]\\s+)(.+)$");
    private static final Pattern PPT_HEADING_PATTERN = Pattern.compile("^##\\s+(.+)$");

    public DocumentPreviewResponse parseDocument(String markdown, String fallbackTitle) {
        String content = normalize(markdown);
        List<String> warnings = new ArrayList<>();
        if (content.isBlank()) {
            warnings.add("markdown 为空，返回空白预览");
        }

        List<MarkdownSection> markdownSections = splitSections(content, warnings);
        if (markdownSections.isEmpty()) {
            markdownSections = Collections.singletonList(new MarkdownSection(1, fallbackTitle, List.of()));
        }

        List<DocumentPreviewResponse.OutlineItem> outline = new ArrayList<>();
        List<DocumentPreviewResponse.Section> sections = new ArrayList<>();
        String title = fallbackTitle;
        boolean titleResolved = false;

        for (int i = 0; i < markdownSections.size(); i++) {
            MarkdownSection markdownSection = markdownSections.get(i);
            String sectionId = "sec-" + (i + 1);
            outline.add(DocumentPreviewResponse.OutlineItem.builder()
                    .id(sectionId)
                    .level(markdownSection.level)
                    .title(markdownSection.title)
                    .build());

            if (!titleResolved && markdownSection.level == 1 && !markdownSection.title.isBlank()) {
                title = markdownSection.title;
                titleResolved = true;
            }

            sections.add(DocumentPreviewResponse.Section.builder()
                    .id(sectionId)
                    .level(markdownSection.level)
                    .title(markdownSection.title)
                    .blocks(parseBlocks(markdownSection.bodyLines, sectionId))
                    .build());
        }

        if (!titleResolved && !markdownSections.isEmpty() && !markdownSections.get(0).title.isBlank()) {
            title = markdownSections.get(0).title;
        }

        if (outline.isEmpty()) {
            warnings.add("未识别到任何标题，已按单节文档返回");
        }

        return DocumentPreviewResponse.builder()
                .artifactType("DOCUMENT")
                .title(title)
                .rawMarkdown(content)
                .generatedAt(Instant.now().toString())
                .outline(outline)
                .sections(sections)
                .warnings(warnings)
                .build();
    }

    public PresentationPreviewResponse parsePresentation(String markdown, String fallbackTitle) {
        String content = normalize(markdown);
        List<String> warnings = new ArrayList<>();
        if (content.isBlank()) {
            warnings.add("markdown 为空，返回空白预览");
        }

        List<MarkdownSection> slides = splitSlides(content, warnings);
        if (slides.isEmpty()) {
            slides = Collections.singletonList(new MarkdownSection(1, fallbackTitle, List.of()));
        }

        List<PresentationPreviewResponse.Slide> slideViews = new ArrayList<>();
        String title = fallbackTitle;
        boolean titleResolved = false;

        for (int i = 0; i < slides.size(); i++) {
            MarkdownSection slide = slides.get(i);
            if (!titleResolved && !slide.title.isBlank()) {
                title = slide.title;
                titleResolved = true;
            }

            slideViews.add(PresentationPreviewResponse.Slide.builder()
                    .id("slide-" + (i + 1))
                    .slideNo(i + 1)
                    .title(slide.title)
                    .bodyMarkdown(joinLines(slide.bodyLines))
                    .bullets(extractBullets(slide.bodyLines))
                    .build());
        }

        return PresentationPreviewResponse.builder()
                .artifactType("PRESENTATION")
                .title(title)
                .rawMarkdown(content)
                .generatedAt(Instant.now().toString())
                .pageCount(slideViews.size())
                .slides(slideViews)
                .warnings(warnings)
                .build();
    }

    private List<MarkdownSection> splitSections(String markdown, List<String> warnings) {
        List<MarkdownSection> sections = new ArrayList<>();
        String[] lines = markdown.split("\\R", -1);

        String currentTitle = null;
        int currentLevel = 0;
        List<String> currentBody = new ArrayList<>();

        for (String line : lines) {
            Matcher matcher = HEADING_PATTERN.matcher(line);
            if (matcher.matches()) {
                if (currentTitle != null) {
                    sections.add(new MarkdownSection(currentLevel, currentTitle, new ArrayList<>(currentBody)));
                }
                currentLevel = matcher.group(1).length();
                currentTitle = matcher.group(2).trim();
                currentBody = new ArrayList<>();
            } else {
                currentBody.add(line);
            }
        }

        if (currentTitle != null) {
            sections.add(new MarkdownSection(currentLevel, currentTitle, new ArrayList<>(currentBody)));
        }

        if (sections.isEmpty() && !markdown.isBlank()) {
            warnings.add("未检测到标题，已将全文作为单节内容");
            sections.add(new MarkdownSection(1, "内容", List.of(markdown)));
        }

        return sections;
    }

    private List<MarkdownSection> splitSlides(String markdown, List<String> warnings) {
        List<MarkdownSection> slides = new ArrayList<>();
        String[] lines = markdown.split("\\R", -1);

        String currentTitle = null;
        List<String> currentBody = new ArrayList<>();
        List<String> preface = new ArrayList<>();

        for (String line : lines) {
            Matcher matcher = PPT_HEADING_PATTERN.matcher(line);
            if (matcher.matches()) {
                if (currentTitle != null) {
                    slides.add(new MarkdownSection(2, currentTitle, new ArrayList<>(currentBody)));
                }
                currentTitle = matcher.group(1).trim();
                currentBody = new ArrayList<>();
            } else if (currentTitle == null) {
                preface.add(line);
            } else {
                currentBody.add(line);
            }
        }

        if (currentTitle != null) {
            slides.add(new MarkdownSection(2, currentTitle, new ArrayList<>(currentBody)));
        }

        if (slides.isEmpty()) {
            warnings.add("未检测到 ## 级标题，已将全文作为单页内容");
            slides.add(new MarkdownSection(2, "PPT", List.of(markdown)));
        } else if (preface.stream().anyMatch(line -> !line.isBlank())) {
            warnings.add("在第一个 ## 标题之前存在内容，已保留在原始 markdown 中但未单独拆页");
        }

        return slides;
    }

    private List<DocumentPreviewResponse.Block> parseBlocks(List<String> lines, String sectionId) {
        List<DocumentPreviewResponse.Block> blocks = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        List<String> bullets = new ArrayList<>();
        int blockIndex = 0;
        BlockType currentType = BlockType.NONE;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine;
            if (line.isBlank()) {
                blockIndex = flushBlock(blocks, sectionId, blockIndex, currentType, paragraph, bullets);
                currentType = BlockType.NONE;
                continue;
            }

            Matcher bulletMatcher = BULLET_PATTERN.matcher(line.trim());
            if (bulletMatcher.matches()) {
                if (currentType == BlockType.PARAGRAPH) {
                    blockIndex = flushBlock(blocks, sectionId, blockIndex, currentType, paragraph, bullets);
                }
                currentType = BlockType.LIST;
                bullets.add(bulletMatcher.group(1).trim());
            } else {
                if (currentType == BlockType.LIST) {
                    blockIndex = flushBlock(blocks, sectionId, blockIndex, currentType, paragraph, bullets);
                }
                currentType = BlockType.PARAGRAPH;
                if (paragraph.length() > 0) {
                    paragraph.append(' ');
                }
                paragraph.append(line.trim());
            }
        }

        flushBlock(blocks, sectionId, blockIndex, currentType, paragraph, bullets);
        return blocks;
    }

    private int flushBlock(List<DocumentPreviewResponse.Block> blocks,
                           String sectionId,
                           int blockIndex,
                           BlockType currentType,
                           StringBuilder paragraph,
                           List<String> bullets) {
        if (currentType == BlockType.PARAGRAPH && paragraph.length() > 0) {
            blocks.add(DocumentPreviewResponse.Block.builder()
                    .id(sectionId + "-blk-" + (blockIndex + 1))
                    .type("paragraph")
                    .text(paragraph.toString().trim())
                    .items(List.of())
                    .build());
            paragraph.setLength(0);
            return blockIndex + 1;
        }
        if (currentType == BlockType.LIST && !bullets.isEmpty()) {
            blocks.add(DocumentPreviewResponse.Block.builder()
                    .id(sectionId + "-blk-" + (blockIndex + 1))
                    .type("list")
                    .text(null)
                    .items(new ArrayList<>(bullets))
                    .build());
            bullets.clear();
            return blockIndex + 1;
        }
        paragraph.setLength(0);
        bullets.clear();
        return blockIndex;
    }

    private List<String> extractBullets(List<String> lines) {
        List<String> bullets = new ArrayList<>();
        for (String rawLine : lines) {
            if (rawLine == null) {
                continue;
            }
            Matcher bulletMatcher = BULLET_PATTERN.matcher(rawLine.trim());
            if (bulletMatcher.matches()) {
                bullets.add(bulletMatcher.group(1).trim());
            }
        }
        return bullets;
    }

    private String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append(line);
        }
        return builder.toString().trim();
    }

    private String normalize(String markdown) {
        if (markdown == null) {
            return "";
        }
        return markdown.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private enum BlockType {
        NONE,
        PARAGRAPH,
        LIST
    }

    private record MarkdownSection(int level, String title, List<String> bodyLines) {
    }
}

