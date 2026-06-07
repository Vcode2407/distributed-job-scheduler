package com.example.scheduler.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.scheduler.application.port.in.CreateJobCommand;
import com.example.scheduler.application.port.in.JobFilter;
import com.example.scheduler.application.service.JobApplicationService;
import com.example.scheduler.domain.model.Job;
import com.example.scheduler.domain.model.JobState;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JobController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(JobControllerTest.TestClockConfig.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobApplicationService jobs;

    @Test
    void createsJobWithIdempotencyHeader() throws Exception {
        Job job = sampleJob(JobState.QUEUED);
        when(jobs.createJob(any(CreateJobCommand.class))).thenReturn(job);

        mockMvc.perform(post("/api/jobs")
                        .header("Idempotency-Key", "request-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "billing",
                                  "payload": {"tenant":"acme"},
                                  "queueName": "default",
                                  "priority": 20
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/jobs/" + job.id()))
                .andExpect(jsonPath("$.state").value("QUEUED"));

        ArgumentCaptor<CreateJobCommand> captor = ArgumentCaptor.forClass(CreateJobCommand.class);
        verify(jobs).createJob(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().idempotencyKey()).isEqualTo("request-key");
    }

    @Test
    void listsJobs() throws Exception {
        when(jobs.listJobs(any(JobFilter.class))).thenReturn(List.of(sampleJob(JobState.RUNNING)));
        when(jobs.countJobs(any(JobFilter.class))).thenReturn(1L);

        mockMvc.perform(get("/api/jobs?state=RUNNING&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].state").value("RUNNING"));
    }

    private Job sampleJob(JobState state) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        return new Job(
                UUID.randomUUID(),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "billing",
                "{}",
                state,
                20,
                now,
                null,
                0,
                3,
                30,
                3600,
                "request-key",
                null,
                null,
                null,
                now,
                now,
                0
        );
    }

    @TestConfiguration
    static class TestClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-06-07T00:00:00Z"), ZoneOffset.UTC);
        }
    }
}
