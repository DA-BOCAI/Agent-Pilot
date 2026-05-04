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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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

        mockMvc.perform(get("/api/v1/tasks/{taskId}/workspace", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andExpect(jsonPath("$.title").value("接收并标准化用户意图"))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.displayStatus").value("已规划"))
                .andExpect(jsonPath("$.steps[0].code").value("A_CAPTURE"))
                .andExpect(jsonPath("$.steps[0].name").value("接收并标准化用户意图"))
                .andExpect(jsonPath("$.confirmation.waiting").value(false))
                .andExpect(jsonPath("$.preview.available").value(false))
                .andExpect(jsonPath("$.adjustments.available").value(false))
                .andExpect(jsonPath("$.outputs").isArray())
                .andExpect(jsonPath("$.timeline").isArray())
                .andExpect(jsonPath("$.timeline[0].title").value("任务已创建"))
                .andExpect(jsonPath("$.timeline[0].level").value("info"))
                .andExpect(jsonPath("$.debugTask").doesNotExist())
                .andExpect(jsonPath("$.task").doesNotExist())
                .andExpect(jsonPath("$.cards").doesNotExist());

        mockMvc.perform(get("/api/v1/tasks/{taskId}/workspace/stream", taskId))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    void shouldReturnBadRequestWhenUserIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputText\":\"build a launch deck\",\"requestId\":\"req-http-2\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void workspaceCancelShouldStopWaitingTask() throws Exception {
        Mockito.when(planner.plan(Mockito.anyString())).thenReturn(List.of(
                PlanStep.builder().stepId("A_CAPTURE").scene("A").action("理解用户需求").tool("none").requiresConfirm(false).status(StepStatus.PENDING).build(),
                PlanStep.builder().stepId("C_DOC").scene("C").action("生成飞书文档").tool("lark-doc").requiresConfirm(true).status(StepStatus.PENDING).build()
        ));

        String createResponse = mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputText\":\"生成一份项目文档\",\"requestId\":\"req-http-cancel\",\"userId\":\"u-01\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String taskId = objectMapper.readTree(createResponse).get("taskId").asText();
        mockMvc.perform(post("/api/v1/tasks/{taskId}/plan", taskId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/tasks/{taskId}/execute", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAIT_CONFIRM"));

        mockMvc.perform(post("/api/v1/tasks/{taskId}/workspace/cancel", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stepId\":\"C_DOC\",\"comment\":\"前端测试取消\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.displayStatus").value("已取消"))
                .andExpect(jsonPath("$.timeline[?(@.type == 'TASK_CANCELLED')]").exists());
    }

}

