package com.cc.ccojbackend.model.submission;

import com.cc.ccojbackend.model.problem.ProblemCase;
import java.util.List;

public record JudgeTask(String sourceCode, List<ProblemCase> cases, Long timeLimitMs, Long memoryLimitMb) {}
