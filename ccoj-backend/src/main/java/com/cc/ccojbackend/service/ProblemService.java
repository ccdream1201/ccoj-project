package com.cc.ccojbackend.service;

import com.cc.ccojbackend.model.dto.problem.ProblemCreateRequest;
import com.cc.ccojbackend.model.entity.Problem;
import com.baomidou.mybatisplus.spring.service.IService;
import com.cc.ccojbackend.model.vo.user.UserVO;

/**
* @author jiangchengchuan
* @description 针对表【problem(题目表)】的数据库操作Service
* @createDate 2026-11-01 15:53:50
*/
public interface ProblemService extends IService<Problem> {

    Long addProblem(ProblemCreateRequest request, UserVO currentUser);

    void updateProblem(Long id, ProblemCreateRequest request, UserVO currentUser);
}
