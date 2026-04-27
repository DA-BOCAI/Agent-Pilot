package com.hay.agent.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import com.hay.agent.planner.Planner;
import org.mockito.Mockito;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "agent.task.auto-run=false")
@AutoConfigureMockMvc
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private Planner planner;

    @Test
    void shouldCreateAndPlanTask() throws Exception {
        Mockito.when(planner.plan(Mockito.anyString())).thenReturn(List.of(
                PlanStep.builder().stepId("A_CAPTURE").scene("A").action("接收并标准化用户意图").tool("none").requiresConfirm(false).status(StepStatus.PENDING).build(),
                PlanStep.builder().stepId("C_DOC").scene("C").action("生成需求文档初稿").tool("lark-doc").requiresConfirm(true).status(StepStatus.PENDING).build()
        ));

        String createResponse = mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputText\":\"build a launch deck\",\"requestId\":\"req-http-1\",\"userId\":\"u-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(createResponse);
        String taskId = root.get("taskId").asText();

        mockMvc.perform(post("/api/v1/tasks/{taskId}/plan", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLANNED"));

        mockMvc.perform(get("/api/v1/tasks/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId));
    }

    @Test
    void shouldReturnBadRequestWhenUserIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputText\":\"build a launch deck\",\"requestId\":\"req-http-2\"}"))
                .andExpect(status().isBadRequest());
    }

}

