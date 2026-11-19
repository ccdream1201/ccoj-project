package com.cc.ccojbackend.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cc.ccojbackend.common.exception.BusinessException;
import com.cc.ccojbackend.common.result.ResultCode;
import com.cc.ccojbackend.model.entity.Problem;
import com.cc.ccojbackend.model.entity.judge.JudgeConfig;
import com.cc.ccojbackend.model.entity.judge.ProblemCase;
import com.cc.ccojbackend.model.enums.UserRole;
import com.cc.ccojbackend.model.vo.problem.ProblemVO;
import org.springframework.beans.BeanUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import com.cc.ccojbackend.auth.LoginUserProvider;
import com.cc.ccojbackend.common.result.Result;
import com.cc.ccojbackend.model.dto.problem.ProblemCreateRequest;
import com.cc.ccojbackend.model.vo.user.UserVO;
import com.cc.ccojbackend.service.ProblemService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理和查询题目接口
 */
@RestController
@RequestMapping("/problem")
@RequiredArgsConstructor
public class ProblemController {

    private final LoginUserProvider loginUserProvider;
    private final ProblemService problemService;
    private final JsonMapper jsonMapper;

    /**
     * 新增题目，返回题目id
     *
     * @return
     */
    @PostMapping("/add")
    public Result<Long> addProblem(@Valid @RequestBody ProblemCreateRequest problemCreateRequest,
                                   HttpServletRequest request) {

        // 1. 获取当前登陆的用户
        UserVO currentUser = loginUserProvider.getCurrentUser(request);

        Long problemId = problemService.addProblem(problemCreateRequest, currentUser);

        return Result.success(problemId);
    }

    @PutMapping("/{id}")
    public Result<Void> updateProblem(@PathVariable Long id,
                                      @Valid @RequestBody ProblemCreateRequest body,
                                      HttpServletRequest request) {
        problemService.updateProblem(id, body, loginUserProvider.getCurrentUser(request));
        return Result.success();
    }

    @DeleteMapping("/delete")
    public Result<Void> deleteProblem(@RequestParam Long id, HttpServletRequest request) {
        requireAdmin(request);
        if (!problemService.removeById(id)) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return Result.success();
    }

    @PatchMapping("/{id}/publish")
    public Result<Void> publishProblem(@PathVariable Long id, HttpServletRequest request) {
        requireAdmin(request);
        boolean published = problemService.update(Wrappers.<Problem>lambdaUpdate()
                .eq(Problem::getId, id)
                .set(Problem::getStatus, 1));
        if (!published) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<ProblemVO> getProblem(@PathVariable Long id) {
        Problem problem = problemService.getById(id);
        if (problem == null || !Integer.valueOf(1).equals(problem.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return Result.success(toPublicProblem(problem));
    }

    @GetMapping
    public Result<List<ProblemVO>> listProblems(@RequestParam(required = false) Long beforeId) {
        var query = Wrappers.<Problem>lambdaQuery()
                .eq(Problem::getStatus, 1)
                .lt(beforeId != null, Problem::getId, beforeId)
                .orderByDesc(Problem::getId)
                .last("LIMIT 20");
        return Result.success(problemService.list(query).stream().map(this::toPublicProblem).toList());
    }

    private ProblemVO toPublicProblem(Problem problem) {
        ProblemVO result = new ProblemVO();
        BeanUtils.copyProperties(problem, result, "samples", "judgeConfig", "solution");
        result.setSamples(jsonMapper.readValue(problem.getSamples(), new TypeReference<List<ProblemCase>>() {}));
        result.setJudgeConfig(jsonMapper.readValue(problem.getJudgeConfig(), JudgeConfig.class));
        return result;
    }

    private void requireAdmin(HttpServletRequest request) {
        UserVO user = loginUserProvider.getCurrentUser(request);
        if (!UserRole.ADMIN.getValue().equals(user.getUserRole())) {
            throw new BusinessException(ResultCode.NO_PERMISSION);
        }
    }
}
