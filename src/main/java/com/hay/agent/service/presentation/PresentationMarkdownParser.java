package com.hay.agent.service.presentation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PresentationMarkdownParser {

    private static final Pattern SLIDE_HEADING = Pattern.compile("^##\\s+(.+)$");
    private static final Pattern ANY_HEADING = Pattern.compile("^#{1,6}\\s+(.+)$");
    private static final Pattern BULLET = Pattern.compile("^(?:[-*+]\\s+|\\d+[.)]\\s+)(.+)$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$");

    public List<PresentationSlide> parse(String markdown, String fallbackTitle) {
        List<RawSlide> rawSlides = splitSlides(markdown, fallbackTitle);
        List<PresentationSlide> slides = new ArrayList<>();
        for (int i = 0; i < rawSlides.size(); i++) {
            RawSlide raw = rawSlides.get(i);
            slides.add(PresentationSlide.builder()
                    .slideNo(i + 1)
                    .title(cleanInline(raw.title()))
                    .blocks(parseBlocks(raw.lines()))
                    .build());
        }
        return slides;
    }

    private List<RawSlide> splitSlides(String markdown, String fallbackTitle) {
        String content = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n').trim();
        String[] lines = content.split("\\R", -1);
        List<RawSlide> slides = new ArrayList<>();

        String currentTitle = null;
        List<String> currentLines = new ArrayList<>();
        List<String> preface = new ArrayList<>();

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine;
            Matcher matcher = SLIDE_HEADING.matcher(line.trim());
            if (matcher.matches()) {
                if (currentTitle != null) {
                    slides.add(new RawSlide(currentTitle, currentLines));
                }
                currentTitle = matcher.group(1).trim();
                currentLines = slides.isEmpty() ? new ArrayList<>(preface) : new ArrayList<>();
                continue;
            }
            if (currentTitle == null) {
                if (!isOnlyDocumentTitle(line)) {
                    preface.add(line);
                }
            } else {
                currentLines.add(line);
            }
        }

        if (currentTitle != null) {
            slides.add(new RawSlide(currentTitle, currentLines));
        }

        if (slides.isEmpty()) {
            slides.add(new RawSlide(fallbackTitle == null || fallbackTitle.isBlank() ? "PPT" : fallbackTitle, List.of(content)));
        }
        return slides;
    }

    private List<PresentationSlide.SlideBlock> parseBlocks(List<String> lines) {
        List<PresentationSlide.SlideBlock> blocks = new ArrayList<>();
        List<String> paragraph = new ArrayList<>();
        List<String> bullets = new ArrayList<>();
        List<List<String>> tableRows = new ArrayList<>();
        boolean inCodeBlock = false;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock || line.isBlank()) {
                flushTextBlocks(blocks, paragraph, bullets);
                flushTable(blocks, tableRows);
                continue;
            }

            Matcher heading = ANY_HEADING.matcher(line);
            if (heading.matches()) {
                flushTextBlocks(blocks, paragraph, bullets);
                flushTable(blocks, tableRows);
                paragraph.add(heading.group(1).trim());
                continue;
            }

            if (isMarkdownTableRow(line)) {
                flushTextBlocks(blocks, paragraph, bullets);
                if (!TABLE_SEPARATOR.matcher(line).matches()) {
                    tableRows.add(parseTableRow(line));
                }
                continue;
            }

            Matcher bullet = BULLET.matcher(line);
            if (bullet.matches()) {
                flushTable(blocks, tableRows);
                if (!paragraph.isEmpty()) {
                    flushParagraph(blocks, paragraph);
                }
                bullets.add(cleanInline(bullet.group(1).trim()));
                continue;
            }

            flushTable(blocks, tableRows);
            if (!bullets.isEmpty()) {
                flushBullets(blocks, bullets);
            }
            paragraph.add(cleanInline(line));
        }

        flushTextBlocks(blocks, paragraph, bullets);
        flushTable(blocks, tableRows);
        return blocks;
    }

    private void flushTextBlocks(List<PresentationSlide.SlideBlock> blocks, List<String> paragraph, List<String> bullets) {
        if (!paragraph.isEmpty()) {
            flushParagraph(blocks, paragraph);
        }
        if (!bullets.isEmpty()) {
            flushBullets(blocks, bullets);
        }
    }

    private void flushParagraph(List<PresentationSlide.SlideBlock> blocks, List<String> paragraph) {
        blocks.add(PresentationSlide.SlideBlock.builder()
                .type("paragraph")
                .text(String.join(" ", paragraph).trim())
                .items(List.of())
                .rows(List.of())
                .build());
        paragraph.clear();
    }

    private void flushBullets(List<PresentationSlide.SlideBlock> blocks, List<String> bullets) {
        blocks.add(PresentationSlide.SlideBlock.builder()
                .type("bullets")
                .items(new ArrayList<>(bullets))
                .rows(List.of())
                .build());
        bullets.clear();
    }

    private void flushTable(List<PresentationSlide.SlideBlock> blocks, List<List<String>> tableRows) {
        if (tableRows.isEmpty()) {
            return;
        }
        blocks.add(PresentationSlide.SlideBlock.builder()
                .type("table")
                .items(List.of())
                .rows(new ArrayList<>(tableRows))
                .build());
        tableRows.clear();
    }

    private boolean isMarkdownTableRow(String line) {
        return line.startsWith("|") && line.endsWith("|") && line.chars().filter(ch -> ch == '|').count() >= 2;
    }

    private List<String> parseTableRow(String line) {
        String value = line;
        if (value.startsWith("|")) {
            value = value.substring(1);
        }
        if (value.endsWith("|")) {
            value = value.substring(0, value.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        for (String cell : value.split("\\|")) {
            cells.add(cleanInline(cell.trim()));
        }
        return cells;
    }

    private boolean isOnlyDocumentTitle(String line) {
        return line.trim().matches("^#\\s+.+$");
    }

    private String cleanInline(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("__(.+?)__", "$1")
                .replaceAll("`(.+?)`", "$1")
                .replaceAll("!\\[[^]]*]\\([^)]+\\)", "")
                .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")
                .trim();
    }

    private record RawSlide(String title, List<String> lines) {
    }
}
