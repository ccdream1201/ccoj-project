package com.cc.ccojbackend.problem.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.cc.ccojbackend.problem.model.dto.ProblemCreateRequest;
import com.cc.ccojbackend.problem.model.entity.Problem;

public interface ProblemService extends IService<Problem> {
    Long addProblem(ProblemCreateRequest request, Long userId);
    void editProblem(Long id, ProblemCreateRequest request);
}
