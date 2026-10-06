package com.campus.agent.config;

import com.campus.agent.llm.DeepSeekClient;
import com.campus.agent.llm.LlmClient;
import com.campus.agent.service.AssistantService;
import com.campus.agent.service.ExtractionEngine;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.mapper.*;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppBeans {

    @Bean
    public ItemRepository itemRepository(AssignmentMapper assignmentMapper, ExamMapper examMapper,
                                         TodoMapper todoMapper, CourseMapper courseMapper,
                                         EventMapper eventMapper, CourseOverrideMapper courseOverrideMapper,
                                         SemesterMapper semesterMapper, InternshipMapper internshipMapper, ProfileMapper profileMapper,
                                         StudyProgressMapper studyProgressMapper , DataSource dataSource) {
        return new ItemRepository(assignmentMapper, examMapper, todoMapper, courseMapper,
                eventMapper, courseOverrideMapper, semesterMapper, internshipMapper, profileMapper, studyProgressMapper, dataSource);
    }

    @Bean
    public LlmClient llmClient(AppProperties props) {
        return new DeepSeekClient(props.apiKey(), props.model(), props.baseUrl());
    }

    @Bean
    public ExtractionEngine extractionEngine(LlmClient llm, ItemRepository repo) {
        return new ExtractionEngine(llm, repo);
    }

    @Bean
    public AssistantService assistantService(LlmClient llm, ItemRepository repo, ExtractionEngine engine) {
        return new AssistantService(llm, repo, engine);
    }
}
