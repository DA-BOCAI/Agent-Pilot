package com.hay.agent.api.dto.preview;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class DocumentPreviewResponse {
    /**
     * 产物类型，固定为document
     */
    String artifactType;

    /**
     * 文档标题，自动根据用户需求生成
     */
    String title;

    /**
     * 原始Markdown内容，用户编辑、同步到飞书都要此字段
     */
    String rawMarkdown;

    /**
     * 生成时间，ISO格式，例如：2023-12-25T12:00:00Z
     */
    String generatedAt;

    /**
     * 文档目录，用于前端渲染侧边栏导航
     */
    List<OutlineItem> outline;

    /**
     * 分章节内容，用于前端分段渲染、区块编辑
     */
    List<Section> sections;

    /**
     * 警告信息，用于提示用户注意，比如内容过程、敏感词等
     */
    List<String> warnings;

    @Value
    @Builder
    public static class OutlineItem {
        /**
         * 目录项ID，唯一标识目录项，用于锚点跳转
         */
        String id;

        /**
         * 标题层级：1=一级标题、2=二级标题、3=三级标题等
         */
        int level;

        /**
         * 标题文本
         */
        String title;
    }

    @Value
    @Builder
    public static class Section {
        /**
         * 分章节ID，唯一标识分章节，用于锚点跳转
         */
        String id;

        /**
         * 章节层级
         */
        int level;

        /**
         * 章节标题文本
         */
        String title;

        /**
         * 章节内容，包含文本、图片、列表等
         */
        List<Block> blocks;
    }

    @Value
    @Builder
    public static class Block {
        /**
         * 区块ID，唯一标识区块，用于锚点跳转
         */
        String id;

        /**
         * 区块类型，例如：text、image、list等
         */
        String type;

        /**
         * 文本内容，例如：段落、列表项等（仅对text类型有效，其他类型为空）
         */
        String text;

        /**
         * 列表项内容，例如：列表项1、列表项2等（仅对list类型有效，其他类型为空）
         */
        List<String> items;
    }
}

