package com.hay.agent.api.dto.preview;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PresentationPreviewResponse {
    /**
     * 产物类型，固定为presentation
     */
    String artifactType;

    /**
     * 演示文稿标题，自动根据用户需求生成
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
     * 当前演示文稿主题：business / tech / campaign / minimal
     */
    String theme;

    /**
     * 演示文稿总页数
     */
    int pageCount;

    /**
     * 演示文稿分页内容，每个分页包含标题、Markdown内容、列表等
     */
    List<Slide> slides;

    /**
     * 警告信息，用于提示用户注意，比如内容过程、敏感词等
     */
    List<String> warnings;

    @Value
    @Builder
    public static class Slide {
        /**
         * 分页ID，唯一标识分页，用于锚点跳转
         */
        String id;

        /**
         * 分页序号，从1开始
         */
        int slideNo;

        /**
         * 分页标题
         */
        String title;

        /**
         * 分页Markdown内容
         */
        String bodyMarkdown;

        /**
         * 分页列表内容，用于前端渲染列表项
         */
        List<String> bullets;

        /**
         * 分页结构化内容块，便于前端后续做精细编辑。
         */
        List<Block> blocks;
    }

    @Value
    @Builder
    public static class Block {
        String type;
        String text;
        List<String> items;
        List<List<String>> rows;
    }
}

