package com.example.contextengine.domain.pipeline;

import com.example.contextengine.domain.model.StepResult;

public interface ContextStep {

    String featureName();

    StepResult execute(PipelineContext context);
}
