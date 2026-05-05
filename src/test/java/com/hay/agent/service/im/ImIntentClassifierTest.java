package com.hay.agent.service.im;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImIntentClassifierTest {

    private final ImIntentClassifier classifier = new ImIntentClassifier();

    @Test
    void shouldTreatVagueCapabilityQuestionsAsHelpQueries() {
        String[] questions = {
                "你能帮我做个PPT吗？",
                "可以帮我生成一份报告吗",
                "你会不会做演示稿",
                "能不能帮忙写个文档？",
                "你可以做周报吗"
        };

        for (String question : questions) {
            assertEquals(ImIntentType.HELP_QUERY, classifier.classify(question, false).getType(), question);
        }
    }

    @Test
    void shouldStillCreateTaskForConcretePoliteRequests() {
        String[] requests = {
                "你能帮我做一份面向销售团队的新品发布PPT吗，包含产品亮点、上市节奏和风险预案",
                "可以帮我生成一份关于AI Agent项目复盘的报告吗，重点写进展、风险和下一步",
                "能不能帮忙写个校招宣讲文档，面向研发候选人，包含岗位亮点和流程"
        };

        for (String request : requests) {
            assertEquals(ImIntentType.NEW_TASK, classifier.classify(request, false).getType(), request);
        }
    }

    @Test
    void shouldAskClarificationForShortImperativeProductRequests() {
        assertEquals(ImIntentType.CLARIFICATION_NEEDED, classifier.classify("帮我做个PPT", false).getType());
    }
}
