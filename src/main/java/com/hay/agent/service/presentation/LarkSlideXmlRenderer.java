package com.hay.agent.service.presentation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LarkSlideXmlRenderer {

    private static final Pattern METRIC_TOKEN_PATTERN = Pattern.compile("([+-]?\\d+(?:\\.\\d+)?\\s*(?:%|万|亿|w|W|K|k)?|TOP\\s*\\d+|top\\s*\\d+)");
    private static final Pattern DECORATIVE_SYMBOLS = Pattern.compile("[✅✔☑📌🚀⭐✨🔥💡🎯👉▶▷►▸▪▫●■◆◇]");

    public String render(PresentationSlide slide, String deckTitle, PresentationTheme theme) {
        PresentationTheme actualTheme = theme == null ? PresentationTheme.BUSINESS : theme;
        PresentationLayout layout = resolveLayout(slide);
        return "<slide xmlns=\"http://www.larkoffice.com/sml/2.0\">"
                + "<style><fill><fillColor color=\"" + actualTheme.getBackground() + "\"/></fill></style>"
                + "<data>"
                + renderLayout(slide, deckTitle, actualTheme, layout)
                + "</data></slide>";
    }

    private String renderLayout(PresentationSlide slide, String deckTitle, PresentationTheme theme, PresentationLayout layout) {
        return switch (layout) {
            case COVER -> coverLayout(slide, deckTitle, theme);
            case METRIC_CARDS -> standardHeader(slide, theme) + metricCards(slide, theme) + footerShape(deckTitle, slide.getSlideNo(), theme);
            case TIMELINE -> standardHeader(slide, theme) + timeline(slide, theme) + footerShape(deckTitle, slide.getSlideNo(), theme);
            case COMPARISON_TABLE -> standardHeader(slide, theme) + comparisonTable(slide, theme) + footerShape(deckTitle, slide.getSlideNo(), theme);
            case SECTION_DIVIDER -> sectionDividerLayout(slide, deckTitle, theme);
            case TWO_COLUMN -> standardHeader(slide, theme)
                    + (isAgendaTitle(slide.getTitle()) ? agendaLayout(slide, theme) : twoColumnContent(slide, theme))
                    + footerShape(deckTitle, slide.getSlideNo(), theme);
            case CLOSING -> closingLayout(slide, deckTitle, theme);
            default -> standardHeader(slide, theme) + contentShape(slide, theme) + footerShape(deckTitle, slide.getSlideNo(), theme);
        };
    }

    private PresentationLayout resolveLayout(PresentationSlide slide) {
        PresentationLayout explicit = PresentationLayout.fromCode(slide.getLayout());
        if (explicit != null) {
            return explicit;
        }
        String title = slide.getTitle() == null ? "" : slide.getTitle();
        if (slide.getSlideNo() == 1 || title.contains("封面")) {
            return PresentationLayout.COVER;
        }
        if (title.contains("致谢") || title.contains("总结") || title.toLowerCase().contains("thanks")) {
            return PresentationLayout.CLOSING;
        }
        if (isSectionDividerCandidate(slide)) {
            return PresentationLayout.SECTION_DIVIDER;
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
        return PresentationLayout.CONTENT;
    }

    private boolean hasMetricBullets(PresentationSlide slide) {
        for (PresentationSlide.SlideBlock block : slide.getBlocks()) {
            if (!"bullets".equals(block.getType()) || block.getItems() == null || block.getItems().size() < 2) {
                continue;
            }
            long metricCount = block.getItems().stream().filter(this::isMetricCandidate).count();
            if (metricCount >= 2) {
                return true;
            }
        }
        return false;
    }

    private boolean isMetricCandidate(String text) {
        if (text == null) {
            return false;
        }
        String value = cleanText(text);
        Matcher matcher = METRIC_TOKEN_PATTERN.matcher(value);
        if (!matcher.find()) {
            return false;
        }
        return value.length() <= 34 || value.contains("%") || value.contains("万") || value.contains("亿") || value.toLowerCase().contains("top");
    }

    private boolean hasTimelineTable(PresentationSlide slide) {
        for (PresentationSlide.SlideBlock block : slide.getBlocks()) {
            if ("table".equals(block.getType()) && block.getRows() != null && block.getRows().size() >= 2) {
                String headerText = String.join(" ", block.getRows().get(0));
                return headerText.contains("阶段") || headerText.contains("时间") || headerText.toLowerCase().contains("phase");
            }
        }
        return false;
    }

    private boolean hasComparisonTable(PresentationSlide slide) {
        for (PresentationSlide.SlideBlock block : slide.getBlocks()) {
            if ("table".equals(block.getType()) && block.getRows() != null && block.getRows().size() >= 2) {
                String headerText = String.join(" ", block.getRows().get(0));
                return headerText.contains("维度") || headerText.contains("对比")
                        || headerText.contains("现状") || headerText.contains("目标")
                        || headerText.contains("风险") || headerText.contains("影响") || headerText.contains("应对")
                        || headerText.toLowerCase().contains("compare");
            }
        }
        return false;
    }

    private String coverLayout(PresentationSlide slide, String deckTitle, PresentationTheme theme) {
        String subtitle = slide.getBlocks().stream()
                .filter(block -> block.getText() != null && !block.getText().isBlank())
                .map(PresentationSlide.SlideBlock::getText)
                .findFirst()
                .orElse(deckTitle);
        return "<shape type=\"rect\" topLeftX=\"690\" topLeftY=\"0\" width=\"270\" height=\"540\">"
                + "<fill><fillColor color=\"" + softSurface(theme) + "\"/></fill>"
                + "</shape>"
                + "<shape type=\"rect\" topLeftX=\"70\" topLeftY=\"82\" width=\"8\" height=\"332\">"
                + "<fill><fillColor color=\"" + theme.getAccent() + "\"/></fill>"
                + "</shape>"
                + "<shape type=\"rect\" topLeftX=\"104\" topLeftY=\"92\" width=\"86\" height=\"4\">"
                + "<fill><fillColor color=\"" + theme.getAccent() + "\"/></fill>"
                + "</shape>"
                + "<shape type=\"text\" topLeftX=\"104\" topLeftY=\"112\" width=\"300\" height=\"30\">"
                + "<content textType=\"caption\" fontSize=\"12\" color=\"" + theme.getAccent() + "\" bold=\"true\"><p>DEMO READY DECK</p></content></shape>"
                + "<shape type=\"text\" topLeftX=\"104\" topLeftY=\"150\" width=\"670\" height=\"122\">"
                + "<content textType=\"title\" fontSize=\"" + coverTitleFont(deckTitle) + "\" color=\"" + theme.getTitleColor() + "\" bold=\"true\" lineSpacing=\"multiple:1.05\"><p>"
                + escapeXml(compactForSlide(deckTitle, 30))
                + "</p></content></shape>"
                + "<shape type=\"text\" topLeftX=\"108\" topLeftY=\"310\" width=\"590\" height=\"72\">"
                + "<content textType=\"sub-headline\" fontSize=\"19\" color=\"" + theme.getBodyColor() + "\" lineSpacing=\"multiple:1.16\"><p>"
                + escapeXml(compactForSlide(subtitle, 52))
                + "</p></content></shape>"
                + "<shape type=\"text\" topLeftX=\"732\" topLeftY=\"392\" width=\"150\" height=\"44\">"
                + "<content textType=\"caption\" fontSize=\"13\" color=\"" + theme.getBodyColor() + "\" textAlign=\"right\"><p>IM to Doc and Slides</p></content></shape>"
                + footerShape(deckTitle, slide.getSlideNo(), theme);
    }

    private String closingLayout(PresentationSlide slide, String deckTitle, PresentationTheme theme) {
        String body = slide.getBlocks().stream()
                .map(block -> block.getText() == null ? "" : cleanText(block.getText()))
                .filter(text -> !text.isBlank())
                .findFirst()
                .orElse("感谢聆听");
        return "<shape type=\"rect\" topLeftX=\"72\" topLeftY=\"104\" width=\"86\" height=\"4\">"
                + "<fill><fillColor color=\"" + theme.getAccent() + "\"/></fill>"
                + "</shape>"
                + "<shape type=\"text\" topLeftX=\"72\" topLeftY=\"148\" width=\"650\" height=\"92\">"
                + "<content textType=\"title\" fontSize=\"42\" color=\"" + theme.getTitleColor() + "\" bold=\"true\" lineSpacing=\"multiple:1.05\"><p>"
                + escapeXml(compactForSlide(slide.getTitle(), 22))
                + "</p></content></shape>"
                + "<shape type=\"text\" topLeftX=\"76\" topLeftY=\"282\" width=\"620\" height=\"86\">"
                + "<content textType=\"body\" fontSize=\"18\" color=\"" + theme.getBodyColor() + "\" lineSpacing=\"multiple:1.2\"><p>"
                + escapeXml(compactForSlide(body, 58))
                + "</p></content></shape>"
                + "<shape type=\"rect\" topLeftX=\"724\" topLeftY=\"118\" width=\"96\" height=\"260\">"
                + "<fill><fillColor color=\"" + softSurface(theme) + "\"/></fill>"
                + "<border color=\"" + softBorder(theme) + "\" width=\"1\"/>"
                + "</shape>"
                + "<shape type=\"rect\" topLeftX=\"820\" topLeftY=\"118\" width=\"44\" height=\"260\">"
                + "<fill><fillColor color=\"" + theme.getAccent() + "\"/></fill>"
                + "</shape>"
                + footerShape(deckTitle, slide.getSlideNo(), theme);
    }

    private String sectionDividerLayout(PresentationSlide slide, String deckTitle, PresentationTheme theme) {
        String kicker = firstBullet(slide).isBlank() ? "NEXT SECTION" : compactForSlide(firstBullet(slide), 38);
        return "<shape type=\"rect\" topLeftX=\"0\" topLeftY=\"0\" width=\"250\" height=\"540\">"
                + "<fill><fillColor color=\"" + softSurface(theme) + "\"/></fill>"
                + "</shape>"
                + "<shape type=\"text\" topLeftX=\"78\" topLeftY=\"138\" width=\"92\" height=\"52\">"
                + "<content textType=\"headline\" fontSize=\"32\" color=\"" + theme.getAccent() + "\" bold=\"true\"><p>"
                + String.format("%02d", Math.max(1, slide.getSlideNo()))
                + "</p></content></shape>"
                + "<shape type=\"rect\" topLeftX=\"78\" topLeftY=\"206\" width=\"96\" height=\"4\">"
                + "<fill><fillColor color=\"" + theme.getAccent() + "\"/></fill>"
                + "</shape>"
                + "<shape type=\"text\" topLeftX=\"302\" topLeftY=\"154\" width=\"560\" height=\"118\">"
                + "<content textType=\"title\" fontSize=\"40\" color=\"" + theme.getTitleColor() + "\" bold=\"true\" lineSpacing=\"multiple:1.05\"><p>"
                + escapeXml(compactForSlide(slide.getTitle(), 24))
                + "</p></content></shape>"
                + "<shape type=\"text\" topLeftX=\"306\" topLeftY=\"304\" width=\"510\" height=\"58\">"
                + "<content textType=\"caption\" fontSize=\"16\" color=\"" + theme.getBodyColor() + "\" lineSpacing=\"multiple:1.15\"><p>"
                + escapeXml(kicker)
                + "</p></content></shape>"
                + footerShape(deckTitle, slide.getSlideNo(), theme);
    }

    private String standardHeader(PresentationSlide slide, PresentationTheme theme) {
        return accentBar(theme) + titleShape(slide.getTitle(), theme);
    }

    private String accentBar(PresentationTheme theme) {
        return "<shape type=\"rect\" topLeftX=\"0\" topLeftY=\"0\" width=\"960\" height=\"6\">"
                + "<fill><fillColor color=\"" + theme.getAccent() + "\"/></fill>"
                + "</shape>"
                + "<shape type=\"rect\" topLeftX=\"80\" topLeftY=\"142\" width=\"90\" height=\"4\">"
                + "<fill><fillColor color=\"" + theme.getAccent() + "\"/></fill>"
                + "</shape>";
    }

    private String titleShape(String title, PresentationTheme theme) {
        String safeTitle = compactForSlide(title, 38);
        int fontSize = safeTitle.length() > 30 ? 22 : safeTitle.length() > 22 ? 26 : safeTitle.length() > 14 ? 30 : 34;
        return "<shape type=\"text\" topLeftX=\"80\" topLeftY=\"38\" width=\"800\" height=\"88\">"
                + "<content textType=\"title\" fontSize=\"" + fontSize + "\" color=\"" + theme.getTitleColor() + "\" bold=\"true\" lineSpacing=\"multiple:1.05\">"
                + "<p>" + escapeXml(safeTitle) + "</p>"
                + "</content></shape>";
    }

    private String contentShape(PresentationSlide slide, PresentationTheme theme) {
        List<PresentationSlide.SlideBlock> blocks = slide.getBlocks();
        List<String> bullets = allBullets(blocks);
        if (isExecutiveTitle(slide.getTitle()) && !bullets.isEmpty()) {
            return executiveSummaryLayout(bullets, theme);
        }
        if (bullets.size() >= 2 && bullets.size() <= 4) {
            return insightCards(bullets, theme);
        }

        StringBuilder content = new StringBuilder();
        for (PresentationSlide.SlideBlock block : blocks) {
            if ("bullets".equals(block.getType())) {
                appendBullets(content, block.getItems());
            } else if ("table".equals(block.getType())) {
                appendTableAsText(content, block.getRows());
            } else if (block.getText() != null && !block.getText().isBlank()) {
                content.append("<p>").append(escapeXml(compactForSlide(block.getText(), 80))).append("</p>");
            }
        }
        if (content.isEmpty()) {
            content.append("<p></p>");
        }
        return "<shape type=\"text\" topLeftX=\"96\" topLeftY=\"186\" width=\"760\" height=\"288\">"
                + "<content textType=\"body\" fontSize=\"15\" color=\"" + theme.getBodyColor() + "\" lineSpacing=\"multiple:1.32\">"
                + content
                + "</content></shape>";
    }

    private String executiveSummaryLayout(List<String> bullets, PresentationTheme theme) {
        String lead = compactForSlide(bullets.get(0), 42);
        StringBuilder xml = new StringBuilder();
        xml.append("<shape type=\"text\" topLeftX=\"88\" topLeftY=\"184\" width=\"760\" height=\"88\">")
                .append("<content textType=\"headline\" fontSize=\"25\" color=\"").append(theme.getTitleColor())
                .append("\" bold=\"true\" lineSpacing=\"multiple:1.1\"><p>")
                .append(escapeXml(lead))
                .append("</p></content></shape>")
                .append("<shape type=\"rect\" topLeftX=\"88\" topLeftY=\"288\" width=\"760\" height=\"1\">")
                .append("<fill><fillColor color=\"").append(theme.getAccent()).append("\"/></fill>")
                .append("</shape>");

        int y = 316;
        for (int i = 1; i < Math.min(4, bullets.size()); i++) {
            xml.append("<shape type=\"text\" topLeftX=\"96\" topLeftY=\"").append(y).append("\" width=\"42\" height=\"28\">")
                    .append("<content textType=\"caption\" fontSize=\"14\" color=\"").append(theme.getAccent())
                    .append("\" bold=\"true\"><p>")
                    .append(String.format("%02d", i))
                    .append("</p></content></shape>")
                    .append("<shape type=\"text\" topLeftX=\"146\" topLeftY=\"").append(y - 1).append("\" width=\"690\" height=\"34\">")
                    .append("<content textType=\"body\" fontSize=\"15\" color=\"").append(theme.getBodyColor())
                    .append("\" lineSpacing=\"multiple:1.12\"><p>")
                    .append(escapeXml(compactForSlide(bullets.get(i), 60)))
                    .append("</p></content></shape>");
            y += 48;
        }
        return xml.toString();
    }

    private String insightCards(List<String> bullets, PresentationTheme theme) {
        StringBuilder cards = new StringBuilder();
        int count = Math.min(4, bullets.size());
        boolean grid = count >= 3;
        int startY = grid ? 184 : 190;
        for (int i = 0; i < count; i++) {
            int row = grid ? i / 2 : i;
            int col = grid ? i % 2 : 0;
            int x = grid ? (col == 0 ? 92 : 506) : 104;
            int y = grid ? startY + row * 142 : startY + row * 104;
            int ruleWidth = grid ? 326 : 704;
            int bodyX = grid ? x + 68 : x + 92;
            int bodyWidth = grid ? 278 : 612;
            int bodyHeight = grid ? 76 : 70;
            String index = String.format("%02d", i + 1);
            cards.append("<line startX=\"").append(x).append("\" startY=\"").append(y + 96)
                    .append("\" endX=\"").append(x + ruleWidth).append("\" endY=\"").append(y + 96)
                    .append("\"><border color=\"").append(softBorder(theme)).append("\" width=\"1\"/></line>")
                    .append("<shape type=\"rect\" topLeftX=\"").append(x).append("\" topLeftY=\"").append(y + 46)
                    .append("\" width=\"44\" height=\"3\">")
                    .append("<fill><fillColor color=\"").append(theme.getAccent()).append("\"/></fill>")
                    .append("</shape>")
                    .append("<shape type=\"text\" topLeftX=\"").append(x).append("\" topLeftY=\"").append(y)
                    .append("\" width=\"54\" height=\"30\">")
                    .append("<content textType=\"headline\" fontSize=\"24\" color=\"").append(theme.getAccent())
                    .append("\" bold=\"true\"><p>")
                    .append(index)
                    .append("</p></content></shape>")
                    .append("<shape type=\"text\" topLeftX=\"").append(bodyX).append("\" topLeftY=\"").append(y + 2)
                    .append("\" width=\"").append(bodyWidth).append("\" height=\"").append(bodyHeight).append("\">")
                    .append("<content textType=\"body\" fontSize=\"").append(grid ? 14 : 16).append("\" color=\"").append(theme.getBodyColor())
                    .append("\" lineSpacing=\"multiple:1.16\"><p>")
                    .append(escapeXml(compactForSlide(bullets.get(i), grid ? 36 : 58)))
                    .append("</p></content></shape>");
        }
        return cards.toString();
    }

    private String agendaLayout(PresentationSlide slide, PresentationTheme theme) {
        List<String> items = allBullets(slide.getBlocks()).stream().limit(6).toList();
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder xml = new StringBuilder();
        int startY = 186;
        for (int i = 0; i < items.size(); i++) {
            int row = i / 2;
            int col = i % 2;
            int x = col == 0 ? 92 : 506;
            int y = startY + row * 78;
            xml.append("<shape type=\"text\" topLeftX=\"").append(x).append("\" topLeftY=\"").append(y)
                    .append("\" width=\"54\" height=\"36\">")
                    .append("<content textType=\"headline\" fontSize=\"23\" color=\"").append(theme.getAccent())
                    .append("\" bold=\"true\"><p>")
                    .append(String.format("%02d", i + 1))
                    .append("</p></content></shape>")
                    .append("<shape type=\"rect\" topLeftX=\"").append(x).append("\" topLeftY=\"").append(y + 44)
                    .append("\" width=\"42\" height=\"3\">")
                    .append("<fill><fillColor color=\"").append(theme.getAccent()).append("\"/></fill>")
                    .append("</shape>")
                    .append("<shape type=\"text\" topLeftX=\"").append(x + 72).append("\" topLeftY=\"").append(y + 2)
                    .append("\" width=\"296\" height=\"52\">")
                    .append("<content textType=\"body\" fontSize=\"16\" color=\"").append(theme.getBodyColor())
                    .append("\" bold=\"true\" lineSpacing=\"multiple:1.15\"><p>")
                    .append(escapeXml(compactForSlide(items.get(i), 26)))
                    .append("</p></content></shape>");
        }
        return xml.toString();
    }

    private String twoColumnContent(PresentationSlide slide, PresentationTheme theme) {
        List<String> items = slide.getBlocks().stream()
                .filter(block -> "bullets".equals(block.getType()) && block.getItems() != null)
                .flatMap(block -> block.getItems().stream())
                .map(this::cleanText)
                .limit(8)
                .toList();
        if (items.size() < 4) {
            return contentShape(slide, theme);
        }

        StringBuilder columns = new StringBuilder();
        int split = (items.size() + 1) / 2;
        columns.append(bulletColumn(items.subList(0, split), 90, theme));
        columns.append(bulletColumn(items.subList(split, items.size()), 500, theme));
        return columns.toString();
    }

    private String bulletColumn(List<String> items, int x, PresentationTheme theme) {
        StringBuilder column = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            int y = 188 + i * 58;
            column.append("<shape type=\"rect\" topLeftX=\"").append(x).append("\" topLeftY=\"").append(y + 7)
                    .append("\" width=\"8\" height=\"8\">")
                    .append("<fill><fillColor color=\"").append(theme.getAccent()).append("\"/></fill>")
                    .append("</shape>")
                    .append("<shape type=\"text\" topLeftX=\"").append(x + 24).append("\" topLeftY=\"").append(y)
                    .append("\" width=\"330\" height=\"46\">")
                    .append("<content textType=\"body\" fontSize=\"15\" color=\"").append(theme.getBodyColor())
                    .append("\" lineSpacing=\"multiple:1.2\"><p>")
                    .append(escapeXml(compactForSlide(items.get(i), 42)))
                    .append("</p></content></shape>");
        }
        return column.toString();
    }

    private String metricCards(PresentationSlide slide, PresentationTheme theme) {
        List<String> items = slide.getBlocks().stream()
                .filter(block -> "bullets".equals(block.getType()) && block.getItems() != null)
                .flatMap(block -> block.getItems().stream())
                .map(this::cleanText)
                .limit(6)
                .toList();
        if (items.isEmpty()) {
            return contentShape(slide, theme);
        }

        StringBuilder cards = new StringBuilder();
        int cardWidth = 236;
        int gap = 30;
        for (int i = 0; i < items.size(); i++) {
            int row = i / 3;
            int col = i % 3;
            int x = 90 + col * (cardWidth + gap);
            int y = 182 + row * 124;
            MetricText metric = splitMetricText(items.get(i));
            cards.append("<shape type=\"rect\" topLeftX=\"").append(x).append("\" topLeftY=\"").append(y)
                    .append("\" width=\"").append(cardWidth).append("\" height=\"104\">")
                    .append("<fill><fillColor color=\"").append(softSurface(theme)).append("\"/></fill>")
                    .append("<border color=\"").append(softBorder(theme)).append("\" width=\"1\"/>")
                    .append("</shape>")
                    .append("<shape type=\"text\" topLeftX=\"").append(x + 18).append("\" topLeftY=\"").append(y + 18)
                    .append("\" width=\"").append(cardWidth - 36).append("\" height=\"32\">")
                    .append("<content textType=\"headline\" fontSize=\"").append(metric.metric() ? 25 : 18)
                    .append("\" color=\"").append(theme.getTitleColor())
                    .append("\" bold=\"true\"><p>")
                    .append(escapeXml(metric.value()))
                    .append("</p></content></shape>")
                    .append("<shape type=\"text\" topLeftX=\"").append(x + 18).append("\" topLeftY=\"").append(y + 58)
                    .append("\" width=\"").append(cardWidth - 36).append("\" height=\"34\">")
                    .append("<content textType=\"caption\" fontSize=\"11\" color=\"").append(theme.getBodyColor())
                    .append("\" lineSpacing=\"multiple:1.08\"><p>")
                    .append(escapeXml(metric.label()))
                    .append("</p></content></shape>");
        }
        return cards.toString();
    }

    private MetricText splitMetricText(String text) {
        String value = cleanText(text);
        Matcher matcher = METRIC_TOKEN_PATTERN.matcher(value);
        if (!matcher.find()) {
            return new MetricText(compactForSlide(value, 18), compactForSlide(value, 34), false);
        }
        String metric = matcher.group(1).replaceAll("\\s+", "");
        String label = (value.substring(0, matcher.start()) + value.substring(matcher.end()))
                .replaceAll("^[：:，,\\s-]+", "")
                .replaceAll("[：:，,\\s-]+$", "")
                .trim();
        if (label.isBlank()) {
            label = value;
        }
        return new MetricText(metric, compactForSlide(label, 34), true);
    }

    private String timeline(PresentationSlide slide, PresentationTheme theme) {
        List<List<String>> rows = slide.getBlocks().stream()
                .filter(block -> "table".equals(block.getType()) && block.getRows() != null && !block.getRows().isEmpty())
                .map(PresentationSlide.SlideBlock::getRows)
                .findFirst()
                .orElse(List.of());
        if (rows.size() <= 1) {
            return contentShape(slide, theme);
        }

        StringBuilder timeline = new StringBuilder();
        int y = 190;
        for (int i = 1; i < rows.size() && i <= 4; i++) {
            List<String> row = rows.get(i);
            String phase = row.isEmpty() ? "阶段" + i : cleanText(row.get(0));
            String time = row.size() > 1 ? cleanText(row.get(1)) : "";
            String action = row.size() > 2 ? cleanText(row.get(2)) : cleanText(String.join(" ", row));
            timeline.append("<shape type=\"rect\" topLeftX=\"105\" topLeftY=\"").append(y + 13)
                    .append("\" width=\"18\" height=\"18\">")
                    .append("<fill><fillColor color=\"").append(theme.getAccent()).append("\"/></fill>")
                    .append("</shape>")
                    .append("<shape type=\"text\" topLeftX=\"145\" topLeftY=\"").append(y)
                    .append("\" width=\"170\" height=\"45\">")
                    .append("<content textType=\"headline\" fontSize=\"19\" color=\"").append(theme.getTitleColor()).append("\" bold=\"true\"><p>")
                    .append(escapeXml(compactForSlide(phase, 12)))
                    .append("</p></content></shape>")
                    .append("<shape type=\"text\" topLeftX=\"320\" topLeftY=\"").append(y + 2)
                    .append("\" width=\"130\" height=\"38\">")
                    .append("<content textType=\"caption\" fontSize=\"12\" color=\"").append(theme.getCaptionColor()).append("\"><p>")
                    .append(escapeXml(compactForSlide(time, 12)))
                    .append("</p></content></shape>")
                    .append("<shape type=\"text\" topLeftX=\"465\" topLeftY=\"").append(y)
                    .append("\" width=\"390\" height=\"48\">")
                    .append("<content textType=\"body\" fontSize=\"14\" color=\"").append(theme.getBodyColor()).append("\" lineSpacing=\"multiple:1.15\"><p>")
                    .append(escapeXml(compactForSlide(action, 46)))
                    .append("</p></content></shape>");
            if (i < rows.size() - 1 && i < 4) {
                timeline.append("<line startX=\"114\" startY=\"").append(y + 34)
                        .append("\" endX=\"114\" endY=\"").append(y + 82)
                        .append("\"><border color=\"").append(theme.getAccent()).append("\" width=\"2\"/></line>");
            }
            y += 80;
        }
        return timeline.toString();
    }

    private String comparisonTable(PresentationSlide slide, PresentationTheme theme) {
        List<List<String>> rows = slide.getBlocks().stream()
                .filter(block -> "table".equals(block.getType()) && block.getRows() != null && !block.getRows().isEmpty())
                .map(PresentationSlide.SlideBlock::getRows)
                .findFirst()
                .orElse(List.of());
        if (rows.size() <= 1) {
            return contentShape(slide, theme);
        }

        StringBuilder table = new StringBuilder();
        int x = 80;
        int y = 182;
        int width = 800;
        int rowHeight = rows.size() > 5 ? 44 : 52;
        int columnCount = Math.max(1, rows.get(0).size());
        int columnWidth = width / columnCount;
        for (int r = 0; r < rows.size() && r < 6; r++) {
            List<String> row = rows.get(r);
            boolean header = r == 0;
            for (int c = 0; c < columnCount; c++) {
                String text = c < row.size() ? cleanText(row.get(c)) : "";
                int cellX = x + c * columnWidth;
                int cellY = y + r * rowHeight;
                String fill = header ? theme.getAccent() : softSurface(theme);
                String color = header ? theme.getBackgroundSafeTextColor() : theme.getBodyColor();
                table.append("<shape type=\"rect\" topLeftX=\"").append(cellX).append("\" topLeftY=\"").append(cellY)
                        .append("\" width=\"").append(columnWidth).append("\" height=\"").append(rowHeight).append("\">")
                        .append("<fill><fillColor color=\"").append(fill).append("\"/></fill>")
                        .append("<border color=\"").append(softBorder(theme)).append("\" width=\"1\"/>")
                        .append("</shape>")
                        .append("<shape type=\"text\" topLeftX=\"").append(cellX + 12).append("\" topLeftY=\"").append(cellY + 10)
                        .append("\" width=\"").append(columnWidth - 24).append("\" height=\"").append(rowHeight - 16).append("\">")
                        .append("<content textType=\"body\" fontSize=\"").append(header ? 13 : 11).append("\" color=\"")
                        .append(color).append("\" bold=\"").append(header).append("\" lineSpacing=\"multiple:1.1\"><p>")
                        .append(escapeXml(compactForSlide(text, columnCount >= 3 ? 20 : 30)))
                        .append("</p></content></shape>");
            }
        }
        return table.toString();
    }

    private void appendBullets(StringBuilder content, List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        content.append("<ul>");
        for (String item : items) {
            content.append("<li><p>").append(escapeXml(compactForSlide(item, 60))).append("</p></li>");
        }
        content.append("</ul>");
    }

    private void appendTableAsText(StringBuilder content, List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            String line = compactForSlide(String.join("    ", rows.get(i)), 76);
            if (i == 0) {
                content.append("<p><strong>").append(escapeXml(line)).append("</strong></p>");
            } else {
                content.append("<p>").append(escapeXml(line)).append("</p>");
            }
        }
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
                        .map(this::cleanText)
                        .forEach(bullets::add);
            }
        }
        return bullets;
    }

    private String firstBullet(PresentationSlide slide) {
        List<String> bullets = allBullets(slide == null ? null : slide.getBlocks());
        return bullets.isEmpty() ? "" : bullets.get(0);
    }

    private boolean isSectionDividerCandidate(PresentationSlide slide) {
        if (slide == null || slide.getBlocks() == null || slide.getBlocks().isEmpty()) {
            return true;
        }
        if (hasTimelineTable(slide) || hasComparisonTable(slide) || hasMetricBullets(slide)) {
            return false;
        }
        List<String> bullets = allBullets(slide.getBlocks());
        long textBlocks = slide.getBlocks().stream()
                .filter(block -> block.getText() != null && !block.getText().isBlank())
                .count();
        return bullets.size() <= 1 && textBlocks <= 1 && cleanText(slide.getTitle()).length() <= 18;
    }

    private int coverTitleFont(String deckTitle) {
        int length = deckTitle == null ? 0 : deckTitle.length();
        if (length > 24) {
            return 32;
        }
        if (length > 16) {
            return 36;
        }
        return 42;
    }

    private String footerShape(String deckTitle, int slideNo, PresentationTheme theme) {
        return "<shape type=\"text\" topLeftX=\"80\" topLeftY=\"500\" width=\"800\" height=\"30\">"
                + "<content textType=\"caption\" fontSize=\"12\" color=\"" + theme.getCaptionColor() + "\">"
                + "<p>" + escapeXml(compactForSlide(deckTitle, 28)) + " | 第" + slideNo + "页</p>"
                + "</content></shape>";
    }

    private String softSurface(PresentationTheme theme) {
        return switch (theme) {
            case BUSINESS -> "rgba(15,118,110,0.08)";
            case MINIMAL -> "rgba(79,70,229,0.07)";
            case TECH -> "rgba(255,255,255,0.10)";
            case CAMPAIGN -> "rgba(255,255,255,0.11)";
        };
    }

    private String softBorder(PresentationTheme theme) {
        return switch (theme) {
            case BUSINESS -> "rgba(15,118,110,0.30)";
            case MINIMAL -> "rgba(79,70,229,0.24)";
            case TECH -> "rgba(45,212,191,0.34)";
            case CAMPAIGN -> "rgba(244,114,92,0.38)";
        };
    }

    private boolean isAgendaTitle(String title) {
        String value = title == null ? "" : title;
        return value.contains("目录") || value.contains("议程") || value.contains("大纲")
                || value.contains("鐩綍") || value.contains("璁▼") || value.contains("澶х翰");
    }

    private boolean isExecutiveTitle(String title) {
        String value = title == null ? "" : title;
        return value.contains("核心结论") || value.contains("摘要") || value.contains("总览")
                || value.contains("鏍稿績缁撹") || value.contains("鎽樿") || value.contains("鎬昏");
    }

    private String cleanText(String value) {
        if (value == null) {
            return "";
        }
        return DECORATIVE_SYMBOLS.matcher(value)
                .replaceAll("")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String compactForSlide(String value, int maxLength) {
        String text = cleanText(value);
        if (text.length() <= maxLength) {
            return text;
        }
        int split = Math.max(text.lastIndexOf('；', maxLength), text.lastIndexOf('，', maxLength));
        if (split >= 8) {
            return text.substring(0, split).trim();
        }
        return text.substring(0, Math.max(1, maxLength - 1)).trim() + "...";
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private record MetricText(String value, String label, boolean metric) {
    }
}
