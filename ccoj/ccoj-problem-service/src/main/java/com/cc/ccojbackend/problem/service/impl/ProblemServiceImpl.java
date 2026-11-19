package com.cc.ccojbackend.problem.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cc.ccojbackend.common.exception.BusinessException;
import com.cc.ccojbackend.common.result.ResultCode;
import com.cc.ccojbackend.problem.mapper.ProblemMapper;
import com.cc.ccojbackend.problem.model.dto.ProblemCreateRequest;
import com.cc.ccojbackend.problem.model.entity.Problem;
import com.cc.ccojbackend.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class ProblemServiceImpl extends ServiceImpl<ProblemMapper, Problem> implements ProblemService {
    private final JsonMapper jsonMapper;

    @Override
    public Long addProblem(ProblemCreateRequest request, Long userId) {
        Problem problem = new Problem();
        apply(problem, request);
        problem.setCreatedBy(userId);
        problem.setStatus(0);
        if (!save(problem)) throw new BusinessException(ResultCode.SYSTEM_ERROR);
        return problem.getId();
    }

    @Override
    public void editProblem(Long id, ProblemCreateRequest request) {
        Problem problem = new Problem();
        problem.setId(id);
        apply(problem, request);
        if (!updateById(problem)) throw new BusinessException(ResultCode.NOT_FOUND);
    }

    private void apply(Problem problem, ProblemCreateRequest input) {
        if (input.getJudgeCases().stream().anyMatch(test -> test == null
                || test.getInput() == null || test.getOutput() == null)
                || input.getSamples().stream().anyMatch(test -> test == null
                || test.getInput() == null || test.getOutput() == null)) {
            throw new BusinessException(ResultCode.PARAMS_ERROR);
        }
        problem.setTitle(input.getTitle());
        problem.setDescription(input.getDescription());
        problem.setDifficulty(input.getDifficulty());
        problem.setSamples(jsonMapper.writeValueAsString(input.getSamples()));
        problem.setJudgeCases(jsonMapper.writeValueAsString(input.getJudgeCases()));
        problem.setJudgeConfig(jsonMapper.writeValueAsString(input.getJudgeConfig()));
        problem.setSolution(input.getSolution() == null ? "" : input.getSolution());
    }
}
