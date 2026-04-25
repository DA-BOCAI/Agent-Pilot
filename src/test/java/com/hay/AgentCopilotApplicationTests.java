package com.hay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "agent.planner.mode=mock",
        "agent.tool.mode=mock",
        "agent.store.redis.enabled=false"
})
class AgentCopilotApplicationTests {

    @Test
    void contextLoads() {
    }

}
