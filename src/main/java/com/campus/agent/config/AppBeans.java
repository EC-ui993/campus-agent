package com.campus.agent.config;

import com.campus.agent.llm.DeepSeekClient;
import com.campus.agent.llm.LlmClient;
import com.campus.agent.service.AssistantService;
import com.campus.agent.store.Database;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.mapper.AssignmentMapper;
import com.campus.agent.store.mapper.CourseMapper;
import com.campus.agent.store.mapper.CourseOverrideMapper;
import com.campus.agent.store.mapper.EventMapper;
import com.campus.agent.store.mapper.ExamMapper;
import com.campus.agent.store.mapper.TodoMapper;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.sql.SQLException;

@Configuration
public class AppBeans {

    @Bean
    public Database database(AppProperties props) throws SQLException {
        return new Database(Path.of(props.dbPath()));
    }

    @Bean
    public ItemRepository itemRepository(AssignmentMapper assignmentMapper, ExamMapper examMapper,
                                         TodoMapper todoMapper, CourseMapper courseMapper,
                                         EventMapper eventMapper, CourseOverrideMapper courseOverrideMapper,
                                         DataSource dataSource) {
        return new ItemRepository(assignmentMapper, examMapper, todoMapper, courseMapper,
                eventMapper, courseOverrideMapper, dataSource);
    }

    @Bean
    public LlmClient llmClient(AppProperties props) {
        return new DeepSeekClient(props.apiKey(), props.model(), props.baseUrl());
    }

    @Bean
    public AssistantService assistantService(LlmClient llm, ItemRepository repo, Database db) {
        return new AssistantService(llm, repo, db);
    }
}
