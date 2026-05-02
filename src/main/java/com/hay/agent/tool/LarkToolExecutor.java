package com.hay.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hay.agent.domain.Artifact;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.service.content.ContentGeneratorService;
import com.hay.agent.service.presentation.LarkSlideXmlRenderer;
import com.hay.agent.service.presentation.PresentationMarkdownParser;
import com.hay.agent.service.presentation.PresentationSlide;
import com.hay.agent.service.presentation.PresentationTheme;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 飞书工具执行器【与飞书生态进行交互】
 * 用于执行飞书相关的任务，如创建文档、演示文稿等。
 */


@Slf4j
@Primary        //MOCK过渡期~
@Component
public class LarkToolExecutor implements ToolExecutor {

    private final ObjectMapper objectMapper;
    private final ContentGeneratorService contentGeneratorService;
    private final PresentationMarkdownParser presentationMarkdownParser;
    private final LarkSlideXmlRenderer larkSlideXmlRenderer;
    private static final String LARK_CLI_PATH = "C:\\Users\\Swiftie\\AppData\\Roaming\\npm\\node_modules\\@larksuite\\cli\\scripts\\run.js";
    private final Duration larkCliTimeout;

    public LarkToolExecutor(ObjectMapper objectMapper,
                            ContentGeneratorService contentGeneratorService,
                            PresentationMarkdownParser presentationMarkdownParser,
                            LarkSlideXmlRenderer larkSlideXmlRenderer,
                            @Value("${agent.tool.lark-cli-timeout:180s}") Duration larkCliTimeout) {
        this.objectMapper = objectMapper;
        this.contentGeneratorService = contentGeneratorService;
        this.presentationMarkdownParser = presentationMarkdownParser;
        this.larkSlideXmlRenderer = larkSlideXmlRenderer;
        this.larkCliTimeout = larkCliTimeout;
    }

    @Override
    public Optional<Artifact> execute(PlanStep step, String taskId, String inputText) {
        log.info("开始执行任务：{}，输入：{}", step.getStepId(), inputText);
        return switch (step.getStepId()) {
            case "C_DOC" -> createLarkDoc(step, taskId, inputText); // 创建飞书文档
            case "D_SLIDES" -> createLarkSlides(step, taskId, inputText); // 创建飞书演示文稿
            case "SEND_IM" -> sendLarkIm(taskId, inputText); // 发送飞书消息
            case "F_DELIVER" -> deliverResult(taskId); // 交付最终结果
            default -> Optional.empty();
        };
    }

    /**
     * 创建飞书文档
     */
    private Optional<Artifact> createLarkDoc(PlanStep step, String taskId, String inputText) {
        try {
            String content = readPreviewRawMarkdown(step.getPreviewData())
                    .orElseGet(() -> contentGeneratorService.generateDocContent(inputText, "需求文档"));
            if (content == null || content.trim().isEmpty()) {
                throw new RuntimeException("调用的内容生成为空，无法创建飞书文档");
            }

            String title = resolveMarkdownTitle(content, "【" + taskId + "】需求文档");

            log.info("开始创建飞书文档，标题：{}，内容长度：{}", title, content.length());

            JsonNode createJson = executeCliJson(List.of(
                    LARK_CLI_PATH,
                    "docs", "+create",
                    "--title", title,
                    "--markdown", "-",
                    "--as", "bot"
            ), "创建飞书文档", content);

            String docUrl = readFirstRequiredText(createJson, "创建文档失败，未返回链接", "/data/doc_url", "/data/url");
            log.info("创建并写入飞书文档成功: {}", docUrl);

            return Optional.of(Artifact.builder()
                    .type("docs")
                    .title(title)
                    .url(docUrl)
                    .build());

        } catch (Exception e) {
            log.error("创建飞书文档失败，直接抛出，停止降级到 Mock", e);
            throw new IllegalStateException("创建飞书文档失败：" + e.getMessage(), e);
        }
    }

    /**
     * 创建飞书演示文稿（PPT）
     */
    private Optional<Artifact> createLarkSlides(PlanStep step, String taskId, String inputText) {
        try {
            String fallbackTitle = "【" + taskId + "】演示文稿";
            JsonNode previewData = step.getPreviewData();
            boolean usePreviewSlides = hasPreviewSlides(previewData);
            String pptContent = readPreviewRawMarkdown(previewData).orElse(null);
            if (!usePreviewSlides && (pptContent == null || pptContent.trim().isEmpty())) {
                pptContent = contentGeneratorService.generatePptContent(inputText, fallbackTitle);
            }
            if ((pptContent == null || pptContent.trim().isEmpty()) && !hasPreviewSlides(previewData)) {
                throw new RuntimeException("调用的PPT内容生成为空，无法创建幻灯片");
            }

            String title = readPreviewTitle(previewData).orElse(null);
            if (title == null || title.isBlank()) {
                title = resolveMarkdownTitle(pptContent, fallbackTitle);
            }
            log.info("【PPT调试】开始创建飞书演示文稿，真实标题：{}，内容长度：{}", title, pptContent == null ? 0 : pptContent.length());

            PresentationTheme theme = resolveTheme(previewData, inputText, title);
            List<PresentationSlide> slides = usePreviewSlides
                    ? readPreviewSlides(previewData)
                    : presentationMarkdownParser.parse(pptContent, title);

            if (slides.size() > 10) {
                throw new IllegalStateException("PPT页数超过Lark CLI单次创建上限10页，当前页数=" + slides.size() + "，请先缩减内容或后续拆分创建");
            }

            JsonNode createJson = executeCliJson(List.of(
                    LARK_CLI_PATH,
                    "slides", "+create",
                    "--title", title,
                    "--as", "bot"
            ), "创建空飞书演示文稿");

            String xmlPresentationId = readFirstRequiredText(createJson, "创建空幻灯片失败，未返回XML演示文稿ID", "/data/xml_presentation_id", "/data/presentation_id");
            String slidesUrl = readFirstRequiredText(createJson, "创建空幻灯片失败，未返回链接", "/data/url");
            log.info("【PPT调试】空PPT创建成功，xmlPresentationId={}，url={}", xmlPresentationId, slidesUrl);

            for (int i = 0; i < slides.size(); i++) {
                PresentationSlide slide = slides.get(i);
                String slideXml = larkSlideXmlRenderer.render(slide, title, theme);
                String paramsJson = escapeCliJsonArgument(createXmlPresentationParams(xmlPresentationId));
                String slideData = objectMapper.writeValueAsString(createSlideData(slideXml));
                log.info("【PPT调试】准备写入第{}页，标题={}，主题={}，XML预览={} ", i + 1, slide.getTitle(), theme.getCode(), truncate(slideXml));

                JsonNode slideJson = executeCliJson(List.of(
                        LARK_CLI_PATH,
                        "slides", "xml_presentation.slide", "create",
                        "--params", paramsJson,
                        "--data", "-",
                        "--as", "bot"
                ), "写入飞书演示文稿第" + (i + 1) + "页", slideData);

                log.info("【PPT调试】第{}页写入结果：{}", i + 1, truncate(slideJson.toString()));
            }

            log.info("创建飞书演示文稿成功: {}", slidesUrl);
            
            return Optional.of(Artifact.builder()
                    .type("slides")
                    .title(title)
                    .url(slidesUrl)
                    .build());

        } catch (Exception e) {
            log.error("创建飞书演示文稿失败，直接抛出，停止降级到 Mock", e);
            throw new IllegalStateException("创建飞书演示文稿失败：" + e.getMessage(), e);
        }
    }

    /**
     * 发送飞书消息
     */
    private Optional<Artifact> sendLarkIm(String taskId, String inputText) {
        log.info("SEND_IM 已由任务卡片链路承载通知，跳过额外 IM CLI 发送，taskId={}", taskId);
        return Optional.empty();
    }

    /**
     * 交付结果
     */
    private Optional<Artifact> deliverResult(String taskId) {
        return Optional.of(Artifact.builder()
                .type("delivery")
                .title("交付包-" + taskId)
                .url("http://localhost:8080/api/tasks/" + taskId)
                .build());
    }

    /**
     * 执行lark-cli命令，无标准输入
     * @param command 命令参数列表
     * @param actionName 操作名称，用于日志
     * @return 解析后的JSON响应
     * @throws Exception 执行异常
     */
    private JsonNode executeCliJson(List<String> command, String actionName) throws Exception {
        return executeCliJson(command, actionName, null);
    }

    /**
     * 执行lark-cli命令，支持标准输入
     * @param command 命令参数列表
     * @param actionName 操作名称，用于日志
     * @param stdinText 标准输入内容
     * @return 解析后的JSON响应
     * @throws Exception 执行异常
     */
    private JsonNode executeCliJson(List<String> command, String actionName, String stdinText) throws Exception {
        // 直接使用node执行JS脚本，绕过CMD解释器避免编码问题
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add("node");
        fullCommand.add(LARK_CLI_PATH);
        fullCommand.addAll(command.subList(1, command.size())); // 跳过原来的第一个元素(lark-cli.cmd)
        
        log.info("开始执行{}，命令：{}，超时时间：{}", actionName, fullCommand, larkCliTimeout);
        Process process = new ProcessBuilder(fullCommand).redirectErrorStream(true).start();

        if (stdinText != null) {
            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(stdinText);
                writer.flush();
            }
        } else {
            process.getOutputStream().close();
        }

        // 先读取所有原始字节，同时尝试GBK和UTF-8两种编码解码
        byte[] outputBytes = process.getInputStream().readAllBytes();
        String outputUtf8 = new String(outputBytes, StandardCharsets.UTF_8);
        String outputGbk = new String(outputBytes, Charset.forName("GBK"));
        
        log.debug("【原始输出调试】字节长度：{}", outputBytes.length);
        log.debug("【UTF-8解码】：{}", outputUtf8.substring(0, Math.min(1000, outputUtf8.length())));
        log.debug("【GBK解码】：{}", outputGbk.substring(0, Math.min(1000, outputGbk.length())));
        
        // 优先使用GBK解码（Windows中文环境默认编码）
        String output = outputGbk.contains("{") || outputGbk.contains("error") ? outputGbk : outputUtf8;

        boolean finished = process.waitFor(larkCliTimeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(actionName + "超时，超过" + larkCliTimeout);
        }

        int exitCode = process.exitValue();
        log.info("{}输出：{}", actionName, truncate(output));

        if (exitCode != 0) {
            throw new IllegalStateException(actionName + "失败，退出码=" + exitCode + "，输出=" + truncate(output));
        }

        int jsonStart = output.indexOf('{');
        if (jsonStart < 0) {
            throw new IllegalStateException(actionName + "失败，输出中未找到JSON，原始输出=" + truncate(output));
        }

        String jsonStr = output.substring(jsonStart);
        return objectMapper.readTree(jsonStr);
    }



    /**
     * 从JSON响应中读取第一个存在的文本字段
     * @param node JSON节点
     * @param errorMessage 错误信息
     * @param pointers JSON指针路径列表
     * @return 找到的文本值
     */
    private String readFirstRequiredText(JsonNode node, String errorMessage, String... pointers) {
        for (String pointer : pointers) {
            JsonNode valueNode = node.at(pointer);
            String value = valueNode.asText("");
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalStateException(errorMessage + "，响应=" + node);
    }

    /**
     * 从Markdown内容中解析标题
     * @param markdown Markdown内容
     * @param fallbackTitle  fallback标题
     * @return 解析出的标题或fallback
     */
    private String resolveMarkdownTitle(String markdown, String fallbackTitle) {
        if (markdown == null || markdown.isBlank()) {
            return fallbackTitle;
        }

        String firstHeading = null;
        for (String line : markdown.split("\\R")) {
            Matcher matcher = Pattern.compile("^(#{1,6})\\s+(.+)$").matcher(line.trim());
            if (!matcher.matches()) {
                continue;
            }

            String heading = matcher.group(2).trim();
            if (heading.isBlank()) {
                continue;
            }

            if (matcher.group(1).length() == 1) {
                return heading;
            }

            if (firstHeading == null) {
                firstHeading = heading;
            }
        }

        return firstHeading != null ? firstHeading : fallbackTitle;
    }

    /**
     * 拆分PPT内容为多页
     * @param pptContent PPT完整内容
     * @return 分页后的内容列表
     */
    private List<String> splitPptPages(String pptContent) {
        String[] rawPages = Pattern.compile("(?=^##\\s)", Pattern.MULTILINE).split(pptContent == null ? "" : pptContent);
        List<String> pages = new ArrayList<>();
        for (String page : rawPages) {
            if (page == null || page.trim().isEmpty()) {
                continue;
            }

            String trimmed = page.trim();
            if (!trimmed.startsWith("## ") && rawPages.length > 1 && isOnlyMarkdownTitle(trimmed)) {
                continue;
            }
            pages.add(trimmed);
        }
        return pages;
    }

    /**
     * 构建幻灯片XML内容
     * @param page 页面内容
     * @param index 页码
     * @param title 文档标题
     * @return 幻灯片XML字符串
     */
    private String buildSlideXml(String page, int index, String title) {
        // 提取页面标题（## 开头的行）
        String pageTitle = "第" + index + "页";
        String pageContent = page;
        Matcher headingMatcher = Pattern.compile("^##\\s+(.+)$", Pattern.MULTILINE).matcher(page);
        if (headingMatcher.find()) {
            pageTitle = headingMatcher.group(1).trim();
            pageContent = page.substring(headingMatcher.end()).trim();
        }
        
        String safeTitle = escapeXml(pageTitle);
        String bodyContent = buildBodyContentXml(pageContent);
        
        // 专业商务风PPT模板：浅灰背景 + 标题 + 内容 + 装饰元素
        return "<slide xmlns=\"http://www.larkoffice.com/sml/2.0\">" +
                // 页面背景
                "<style><fill><fillColor color=\"rgb(248,250,252)\"/></fill></style>" +
                "<data>" +
                // 顶部装饰条
                "<shape type=\"rect\" topLeftX=\"0\" topLeftY=\"0\" width=\"960\" height=\"10\">" +
                "<fill><fillColor color=\"rgb(59,130,246)\"/></fill>" +
                "</shape>" +
                // 页面标题
                "<shape type=\"text\" topLeftX=\"80\" topLeftY=\"40\" width=\"800\" height=\"80\">" +
                "<content textType=\"title\" fontSize=\"36\" color=\"rgb(15,23,42)\" bold=\"true\"><p>" + safeTitle + "</p></content>" +
                "</shape>" +
                // 标题下方分隔线
                "<shape type=\"rect\" topLeftX=\"80\" topLeftY=\"120\" width=\"80\" height=\"4\">" +
                "<fill><fillColor color=\"rgb(59,130,246)\"/></fill>" +
                "</shape>" +
                // 页面内容
                "<shape type=\"text\" topLeftX=\"80\" topLeftY=\"150\" width=\"800\" height=\"350\">" +
                "<content textType=\"body\" fontSize=\"16\" color=\"rgb(30,41,59)\" lineSpacing=\"multiple:1.4\">" + bodyContent + "</content>" +
                "</shape>" +
                // 页脚
                "<shape type=\"text\" topLeftX=\"80\" topLeftY=\"500\" width=\"800\" height=\"30\">" +
                "<content textType=\"caption\" fontSize=\"12\" color=\"rgb(100,116,139)\"><p>" + escapeXml(title) + " | 第" + index + "页</p></content>" +
                "</shape>" +
                "</data></slide>";
    }

    private boolean isOnlyMarkdownTitle(String content) {
        String[] lines = content.split("\\R");
        if (lines.length == 0 || !lines[0].trim().matches("^#\\s+.+$")) {
            return false;
        }
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String buildBodyContentXml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "<p></p>";
        }

        StringBuilder content = new StringBuilder();
        boolean inList = false;
        for (String rawLine : markdown.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("```")) {
                continue;
            }

            Matcher heading = Pattern.compile("^#{1,6}\\s+(.+)$").matcher(line);
            if (heading.matches()) {
                line = heading.group(1).trim();
            }

            Matcher bullet = Pattern.compile("^[-*+]\\s+(.+)$").matcher(line);
            if (bullet.matches()) {
                if (!inList) {
                    content.append("<ul>");
                    inList = true;
                }
                content.append("<li><p>").append(escapeXml(bullet.group(1).trim())).append("</p></li>");
                continue;
            }

            if (inList) {
                content.append("</ul>");
                inList = false;
            }
            content.append("<p>").append(escapeXml(line)).append("</p>");
        }

        if (inList) {
            content.append("</ul>");
        }
        return content.isEmpty() ? "<p></p>" : content.toString();
    }

    /**
     * 创建XML演示文稿请求参数
     * @param xmlPresentationId 演示文稿ID
     * @return 请求参数JSON
     */
    private JsonNode createXmlPresentationParams(String xmlPresentationId) {
        return objectMapper.createObjectNode().put("xml_presentation_id", xmlPresentationId);
    }

    /**
     * 创建幻灯片写入请求数据
     * @param slideXml 幻灯片XML内容
     * @return 请求数据JSON
     */
    private JsonNode createSlideData(String slideXml) {
        return objectMapper.createObjectNode()
                .set("slide", objectMapper.createObjectNode().put("content", slideXml));
    }

    private Optional<String> readPreviewRawMarkdown(JsonNode previewData) {
        if (previewData == null || previewData.isMissingNode() || previewData.isNull()) {
            return Optional.empty();
        }
        String rawMarkdown = previewData.path("rawMarkdown").asText("");
        return rawMarkdown.isBlank() ? Optional.empty() : Optional.of(rawMarkdown);
    }

    private Optional<String> readPreviewTitle(JsonNode previewData) {
        if (previewData == null || previewData.isMissingNode() || previewData.isNull()) {
            return Optional.empty();
        }
        String title = previewData.path("title").asText("");
        return title.isBlank() ? Optional.empty() : Optional.of(title);
    }

    private PresentationTheme resolveTheme(JsonNode previewData, String inputText, String title) {
        if (previewData != null && !previewData.isMissingNode() && !previewData.isNull()) {
            String theme = previewData.path("theme").asText("");
            if (!theme.isBlank()) {
                return PresentationTheme.fromText(theme);
            }
        }
        return PresentationTheme.fromText(inputText + "\n" + title);
    }

    private boolean hasPreviewSlides(JsonNode previewData) {
        return previewData != null
                && !previewData.isMissingNode()
                && !previewData.isNull()
                && previewData.path("slides").isArray()
                && !previewData.path("slides").isEmpty();
    }

    private List<PresentationSlide> readPreviewSlides(JsonNode previewData) {
        List<PresentationSlide> slides = new ArrayList<>();
        JsonNode slideNodes = previewData.path("slides");
        for (int i = 0; i < slideNodes.size(); i++) {
            JsonNode slideNode = slideNodes.get(i);
            slides.add(PresentationSlide.builder()
                    .slideNo(slideNode.path("slideNo").asInt(i + 1))
                    .title(slideNode.path("title").asText("第" + (i + 1) + "页"))
                    .blocks(readPreviewBlocks(slideNode.path("blocks")))
                    .build());
        }
        return slides;
    }

    private List<PresentationSlide.SlideBlock> readPreviewBlocks(JsonNode blockNodes) {
        if (blockNodes == null || !blockNodes.isArray() || blockNodes.isEmpty()) {
            return List.of();
        }
        List<PresentationSlide.SlideBlock> blocks = new ArrayList<>();
        for (JsonNode blockNode : blockNodes) {
            blocks.add(PresentationSlide.SlideBlock.builder()
                    .type(blockNode.path("type").asText("paragraph"))
                    .text(blockNode.path("text").asText(null))
                    .items(readStringList(blockNode.path("items")))
                    .rows(readStringRows(blockNode.path("rows")))
                    .build());
        }
        return blocks;
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            values.add(item.asText(""));
        }
        return values;
    }

    private List<List<String>> readStringRows(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<List<String>> rows = new ArrayList<>();
        for (JsonNode rowNode : node) {
            rows.add(readStringList(rowNode));
        }
        return rows;
    }

    private String escapeCliJsonArgument(JsonNode jsonNode) throws Exception {
        return objectMapper.writeValueAsString(jsonNode).replace("\"", "\\\"");
    }

    /**
     * XML内容转义
     * @param value 原始内容
     * @return 转义后的内容
     */
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

    /**
     * 截断字符串，避免日志过长
     * @param value 原始字符串
     * @return 截断后的字符串
     */
    private String truncate(String value) {
        if (value == null) {
            return "null";
        }
        return value.length() > 1000 ? value.substring(0, 1000) + "..." : value;
    }


}
