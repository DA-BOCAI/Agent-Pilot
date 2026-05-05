package com.hay.agent.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hay.agent.service.im.LarkCardCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lark/cards")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "飞书卡片回调接口", description = "接收飞书消息卡片按钮回调，并推动 Agent 任务继续执行")
public class LarkCardCallbackController {

    private final LarkCardCallbackService callbackService;

    @PostMapping("/callback")
    @Operation(summary = "处理飞书消息卡片回调", description = "用于确认、取消、跳转工作台等消息卡片交互")
    public ResponseEntity<ObjectNode> callback(@RequestBody JsonNode payload) {
        log.info("收到飞书卡片回调请求，payload={}", abbreviate(payload == null ? "" : payload.toString(), 2000));
        return ResponseEntity.ok(callbackService.handleCallback(payload));
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(已截断)";
    }
}
