package com.xiaozhi.integration.langfuse;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.content.Content;
import org.springframework.ai.observation.ObservabilityHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * ChatModel观察过滤器
 * 增强Spring AI的OpenTelemetry追踪数据，添加prompt和completion内容
 * 基于Langfuse官方Spring AI示例
 */
@Component
public class ChatModelObservationFilter implements ObservationFilter {

    @Override
    public Observation.Context map(Observation.Context context) {
        if (!(context instanceof ChatModelObservationContext chatModelObservationContext)) {
            return context;
        }

        var prompts = processPrompts(chatModelObservationContext);
        var completions = processCompletion(chatModelObservationContext);

        // 添加prompt内容到追踪
        chatModelObservationContext.addHighCardinalityKeyValue(new KeyValue() {
            @Override
            public String getKey() {
                return "gen_ai.prompt";
            }

            @Override
            public String getValue() {
                return ObservabilityHelper.concatenateStrings(prompts);
            }
        });

        // 添加completion内容到追踪
        chatModelObservationContext.addHighCardinalityKeyValue(new KeyValue() {
            @Override
            public String getKey() {
                return "gen_ai.completion";
            }

            @Override
            public String getValue() {
                return ObservabilityHelper.concatenateStrings(completions);
            }
        });

        return chatModelObservationContext;
    }

    private List<String> processPrompts(ChatModelObservationContext chatModelObservationContext) {
        return CollectionUtils.isEmpty((chatModelObservationContext.getRequest()).getInstructions()) 
            ? List.of() 
            : (chatModelObservationContext.getRequest()).getInstructions().stream()
                .map(Content::getText)
                .toList();
    }

    private List<String> processCompletion(ChatModelObservationContext context) {
        if (context.getResponse() != null 
            && (context.getResponse()).getResults() != null 
            && !CollectionUtils.isEmpty((context.getResponse()).getResults())) {
            
            return !StringUtils.hasText((context.getResponse()).getResult().getOutput().getText()) 
                ? List.of() 
                : (context.getResponse()).getResults().stream()
                    .filter((generation) -> generation.getOutput() != null 
                        && StringUtils.hasText(generation.getOutput().getText()))
                    .map((generation) -> generation.getOutput().getText())
                    .toList();
        } else {
            return List.of();
        }
    }
}
