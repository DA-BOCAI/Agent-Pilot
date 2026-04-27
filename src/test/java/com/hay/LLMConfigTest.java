package com.hay;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
public class LLMConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testGenerate() {
        ChatLanguageModel chatLanguageModel = applicationContext.getBeanProvider(ChatLanguageModel.class).getIfAvailable();
        assertNull(chatLanguageModel, "当前版本不再创建 ChatLanguageModel Bean，规划链路已切换为显式 HTTP 调用");
    }
}
