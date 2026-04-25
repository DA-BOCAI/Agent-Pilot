package com.hay.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文件作用：Swagger/OpenAPI 元信息配置。
 * 项目角色：定义接口文档标题、描述和联系人等展示信息。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI agentOpenAPI() {
        return new OpenAPI()
                .addTagsItem(new Tag().name("任务编排接口").description("用于任务创建、规划、执行、确认和过程追踪"))
                .info(new Info()
                .title("AgentCopilot 任务编排接口文档")
                .description("用于飞书赛题的任务创建、规划、执行、确认与事件追踪")
                .version("v1.0")
                .contact(new Contact().name("AgentCopilot 开发组")));
    }

    @Bean
    public OpenApiCustomizer chineseOperationCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            updatePost(openApi, "/api/v1/tasks", "创建任务", "根据输入文本创建一个任务实例");
            updatePost(openApi, "/api/v1/tasks/{taskId}/plan", "生成规划", "为指定任务生成可执行步骤清单");
            updatePost(openApi, "/api/v1/tasks/{taskId}/execute", "执行任务", "从当前进度继续执行，直到完成或遇到下一个确认闸门");
            updatePost(openApi, "/api/v1/tasks/{taskId}/confirm", "确认步骤", "对需要人工确认的步骤进行通过或拒绝");
            updateGet(openApi, "/api/v1/tasks/{taskId}", "查询任务详情", "返回任务状态、步骤、产物和事件信息");
            updateGet(openApi, "/api/v1/tasks/{taskId}/events", "查询任务事件", "返回任务执行过程中的完整事件时间线");
        };
    }

    private void updatePost(OpenAPI openApi, String path, String summary, String description) {
        if (openApi.getPaths().get(path) == null || openApi.getPaths().get(path).getPost() == null) {
            return;
        }
        openApi.getPaths().get(path).getPost().setSummary(summary);
        openApi.getPaths().get(path).getPost().setDescription(description);
        openApi.getPaths().get(path).getPost().setTags(java.util.List.of("任务编排接口"));
    }

    private void updateGet(OpenAPI openApi, String path, String summary, String description) {
        if (openApi.getPaths().get(path) == null || openApi.getPaths().get(path).getGet() == null) {
            return;
        }
        openApi.getPaths().get(path).getGet().setSummary(summary);
        openApi.getPaths().get(path).getGet().setDescription(description);
        openApi.getPaths().get(path).getGet().setTags(java.util.List.of("任务编排接口"));
    }
}

