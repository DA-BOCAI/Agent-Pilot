package com.hay.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hay.agent.service.ContentGeneratorService;
import com.hay.agent.service.presentation.LarkSlideXmlRenderer;
import com.hay.agent.service.presentation.PresentationMarkdownParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LarkToolExecutorTest {

    private final LarkToolExecutor executor = new LarkToolExecutor(
            new ObjectMapper(),
            mock(ContentGeneratorService.class),
            new PresentationMarkdownParser(),
            new LarkSlideXmlRenderer(),
            Duration.ofSeconds(1)
    );

    @Test
    void resolveMarkdownTitleShouldPreferFirstHeading() throws Exception {
        assertEquals("产品方案", invokeString("resolveMarkdownTitle", new Class<?>[]{String.class, String.class}, "# 产品方案\n## 背景", "默认标题"));
        assertEquals("目录", invokeString("resolveMarkdownTitle", new Class<?>[]{String.class, String.class}, "## 目录\n内容", "默认标题"));
        assertEquals("默认标题", invokeString("resolveMarkdownTitle", new Class<?>[]{String.class, String.class}, "无标题内容", "默认标题"));
    }

    @Test
    void splitPptPagesShouldSplitByHeadingAndRemoveBlankPages() throws Exception {
        @SuppressWarnings("unchecked")
        List<String> pages = (List<String>) invoke("splitPptPages", new Class<?>[]{String.class}, "## 封面\n内容\n\n## 目录\n- A\n\n## 总结\n- B");

        assertEquals(3, pages.size());
        assertTrue(pages.get(0).contains("封面"));
        assertTrue(pages.get(1).contains("目录"));
        assertTrue(pages.get(2).contains("总结"));
    }

    @Test
    void splitPptPagesShouldIgnoreDocumentTitleBeforeSlides() throws Exception {
        @SuppressWarnings("unchecked")
        List<String> pages = (List<String>) invoke("splitPptPages", new Class<?>[]{String.class}, "# 产品方案\n\n## 封面\n内容\n\n## 总结\n- B");

        assertEquals(2, pages.size());
        assertTrue(pages.get(0).startsWith("## 封面"));
    }

    @Test
    void buildSlideXmlShouldContainEscapedTitleAndBody() throws Exception {
        String xml = invokeString("buildSlideXml", new Class<?>[]{String.class, int.class, String.class}, "## 页面标题\n- A & B", 1, "测试标题");

        assertTrue(xml.contains("测试标题 | 第1页"));
        assertTrue(xml.contains("<fill><fillColor color=\"rgb(59,130,246)\"/></fill>"));
        assertTrue(xml.contains("<content textType=\"title\" fontSize=\"36\" color=\"rgb(15,23,42)\" bold=\"true\"><p>页面标题</p></content>"));
        assertTrue(xml.contains("A &amp; B"));
        assertTrue(xml.contains("<ul><li><p>A &amp; B</p></li></ul>"));
        assertTrue(!xml.contains("height=\"10\" fillColor="));
        assertTrue(!xml.contains("height=\"4\" fillColor="));
        assertTrue(!xml.contains("<p style="));
        assertTrue(xml.startsWith("<slide xmlns=\"http://www.larkoffice.com/sml/2.0\">"));
    }

    @Test
    void createXmlPresentationParamsShouldBeJsonObject() throws Exception {
        JsonNode params = (JsonNode) invoke("createXmlPresentationParams", new Class<?>[]{String.class}, "abc123");
        assertEquals("abc123", params.get("xml_presentation_id").asText());
    }

    private Object invoke(String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = LarkToolExecutor.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(executor, args);
    }

    private String invokeString(String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        return (String) invoke(methodName, paramTypes, args);
    }
}

