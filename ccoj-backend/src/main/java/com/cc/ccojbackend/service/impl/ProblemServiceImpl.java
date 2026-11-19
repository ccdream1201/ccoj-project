package com.cc.ccojbackend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cc.ccojbackend.common.exception.BusinessException;
import com.cc.ccojbackend.common.result.ResultCode;
import com.cc.ccojbackend.model.dto.problem.ProblemCreateRequest;
import com.cc.ccojbackend.model.entity.Problem;
import com.cc.ccojbackend.model.enums.UserRole;
import com.cc.ccojbackend.model.vo.user.UserVO;
import com.cc.ccojbackend.service.ProblemService;
import com.cc.ccojbackend.mapper.ProblemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
* @author jiangchengchuan
* @description 针对表【problem(题目表)】的数据库操作Service实现
* @createDate 2026-11-01 15:53:50
*/
@Service
@RequiredArgsConstructor
public class ProblemServiceImpl extends ServiceImpl<ProblemMapper, Problem>
    implements ProblemService{

    private final JsonMapper jsonMapper;

    @Override
    public Long addProblem(ProblemCreateRequest request, UserVO currentUser) {

        requireAdmin(currentUser);
        Problem newProblem = new Problem();
        applyRequest(newProblem, request);
        newProblem.setCreatedBy(currentUser.getId());
        if (baseMapper.insert(newProblem) != 1 || newProblem.getId() == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
        return newProblem.getId();
    }

    @Override
    public void updateProblem(Long id, ProblemCreateRequest request, UserVO currentUser) {
        requireAdmin(currentUser);
        Problem problem = new Problem();
        problem.setId(id);
        applyRequest(problem, request);
        if (!update(problem, Wrappers.<Problem>lambdaUpdate()
                .eq(Problem::getId, id)
                .set(request.getSolution() == null, Problem::getSolution, null))) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
    }

    private void requireAdmin(UserVO currentUser) {
        if (!UserRole.ADMIN.getValue().equals(currentUser.getUserRole())) {
            throw new BusinessException(ResultCode.NO_PERMISSION);
        }
    }

    private void applyRequest(Problem newProblem, ProblemCreateRequest request) {
        newProblem.setTitle(request.getTitle());
        newProblem.setDescription(request.getDescription());
        newProblem.setDifficulty(request.getDifficulty());
        newProblem.setSamples(jsonMapper.writeValueAsString(request.getSamples()));
        newProblem.setJudgeCases(jsonMapper.writeValueAsString(request.getJudgeCases()));
        newProblem.setJudgeConfig(jsonMapper.writeValueAsString(request.getJudgeConfig()));
        newProblem.setSolution(request.getSolution());
    }
}
