package com.hay.agent.service.im;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ImIntentClassifier {

    private static final Set<String> IGNORE_TEXTS = Set.of(
            "好", "好的", "可以", "收到", "明白", "了解", "谢谢", "辛苦了", "ok", "okay", "yes", "嗯", "行"
    );

    public ImIntentClassification classify(String text, boolean hasActiveTask) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return ImIntentClassification.of(ImIntentType.IGNORE, "空文本", 1.0);
        }

        if (isAcknowledgement(normalized)) {
            return ImIntentClassification.of(ImIntentType.IGNORE, "确认/感谢类消息，不创建任务", 0.95);
        }

        if (looksLikeCancelTask(normalized)) {
            return ImIntentClassification.of(ImIntentType.CANCEL_TASK, "用户希望取消或停止当前任务", 0.92);
        }

        if (looksLikeHelpQuery(normalized)) {
            return ImIntentClassification.of(ImIntentType.HELP_QUERY, "用户询问 Agent 能力或使用方式", 0.88);
        }

        if (looksLikeProgressQuery(normalized)) {
            return ImIntentClassification.of(ImIntentType.PROGRESS_QUERY, "询问当前任务进度或状态", 0.9);
        }

        if (looksLikeRetryTask(normalized)) {
            return ImIntentClassification.of(ImIntentType.RETRY_TASK, "用户希望重试最近失败的任务", 0.86);
        }

        if (!hasActiveTask && looksLikeUnderspecifiedTask(normalized)) {
            return ImIntentClassification.of(ImIntentType.CLARIFICATION_NEEDED, "用户需求过短，需要先澄清目标、对象和内容范围", 0.86);
        }

        if (looksLikeNewTask(normalized)) {
            return ImIntentClassification.of(ImIntentType.NEW_TASK, "包含明确的新任务或产物生成意图", 0.9);
        }

        if (hasActiveTask && looksLikeSupplement(normalized)) {
            return ImIntentClassification.of(ImIntentType.SUPPLEMENT, "当前聊天存在活跃任务，消息像补充约束或调整要求", 0.82);
        }

        if (hasActiveTask && normalized.length() >= 12 && !looksLikeQuestionOnly(normalized)) {
            return ImIntentClassification.of(ImIntentType.SUPPLEMENT, "当前聊天存在活跃任务，较长消息默认作为补充上下文", 0.65);
        }

        return ImIntentClassification.of(ImIntentType.IGNORE, "未识别到明确任务或补充意图", 0.6);
    }

    private boolean looksLikeNewTask(String text) {
        return containsAny(text, "新建", "重新", "另起", "再帮我", "再生成", "再做", "帮我", "帮忙", "请帮", "创建", "生成",
                "写个", "写一份", "做一份", "整理一份", "输出", "起草", "撰写", "制作", "帮我们",
                "加一份", "再加一份", "追加一份", "同时生成", "也生成", "也做一份")
                && containsAny(text, "ppt", "演示文稿", "文档", "doc", "报告", "方案", "纪要", "总结", "日报", "周报", "月报",
                "模板", "大纲", "邮件", "计划");
    }

    private boolean looksLikeSupplement(String text) {
        return containsAny(text, "补充", "另外", "还有", "顺便", "加上", "加入", "增加", "删掉", "删除", "改成",
                "换成", "调整", "优化", "强调", "重点", "主题", "颜色", "风格", "更正式", "更简洁", "不要", "需要包含",
                "里面要", "记得", "别忘了", "限制", "要求");
    }

    private boolean looksLikeProgressQuery(String text) {
        return containsAny(text, "进度", "状态", "做到哪", "到哪了", "完成了吗", "好了没", "生成了吗",
                "现在怎么样", "当前情况", "任务情况", "跑完了吗", "交付了吗", "链接有了吗", "结果呢",
                "怎么样了", "做完了吗", "出来了吗", "卡住了吗", "还要多久", "多久能好", "有结果了吗",
                "到哪一步", "走到哪", "处理完了吗", "progress", "status");
    }

    private boolean looksLikeCancelTask(String text) {
        return containsAny(text, "取消任务", "取消当前任务", "终止任务", "中止任务", "停止任务", "停掉任务",
                "撤回任务", "不用做了", "先别做了", "别做了", "先停一下", "停止生成", "终止生成",
                "cancel", "stop");
    }

    private boolean looksLikeRetryTask(String text) {
        if (!containsAny(text, "重试", "再试", "重新跑", "继续跑", "再跑一次", "retry")) {
            return false;
        }
        return text.length() <= 12 || containsAny(text, "当前任务", "刚才", "上一个", "失败", "这个任务");
    }

    private boolean looksLikeHelpQuery(String text) {
        return containsAny(text, "你能做什么", "能做什么", "怎么用", "如何使用", "使用说明", "帮助",
                "help", "usage", "能力", "功能")
                || looksLikeCapabilityQuestion(text);
    }

    private boolean looksLikeUnderspecifiedTask(String text) {
        if (!containsAny(text, "ppt", "演示稿", "幻灯片", "文档", "报告", "方案")) {
            return false;
        }
        if (!containsAny(text, "帮我", "生成", "做个", "做一份", "写个", "写一份", "搞个", "创建")) {
            return false;
        }
        return text.length() <= 9;
    }

    private boolean looksLikeCapabilityQuestion(String text) {
        if (!containsAny(text, "ppt", "演示稿", "幻灯片", "文档", "报告", "方案", "总结", "周报", "月报")) {
            return false;
        }
        if (!containsAny(text, "你能", "你可以", "能帮", "能不能", "可不可以", "可以帮", "会不会", "会做", "能做", "能生成", "可以生成")) {
            return false;
        }
        if (!containsAny(text, "吗", "么", "不", "能", "可以", "会")) {
            return false;
        }
        return !hasConcreteTaskDetails(text);
    }

    private boolean hasConcreteTaskDetails(String text) {
        return containsAny(text, "面向", "关于", "主题", "项目", "产品", "会议", "复盘", "汇报", "宣讲", "发布会",
                "包含", "包括", "围绕", "重点", "风格", "页", "章节", "背景", "目标", "进展", "风险", "计划", "下一步",
                "销售", "管理层", "客户", "团队", "校招", "竞品", "时间线");
    }

    private boolean looksLikeQuestionOnly(String text) {
        return text.endsWith("?") || text.endsWith("？")
                || containsAny(text, "吗", "是不是", "为什么", "怎么", "如何");
    }

    private boolean isAcknowledgement(String text) {
        return IGNORE_TEXTS.contains(text)
                || (text.length() <= 8 && containsAny(text, "谢谢", "收到", "好的", "ok", "辛苦"));
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return text == null ? "" : text
                .replaceAll("\\s+", "")
                .replaceAll("[，。！？!?,.；;：:、]", "")
                .toLowerCase();
    }
}
