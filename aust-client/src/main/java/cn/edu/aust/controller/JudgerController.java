package cn.edu.aust.controller;

import com.github.pagehelper.PageInfo;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URLDecoder;
import java.util.List;
import java.util.Objects;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import cn.edu.aust.common.constant.PosCode;
import cn.edu.aust.common.constant.ProblemType;
import cn.edu.aust.common.entity.ResultVO;
import cn.edu.aust.common.util.CgiHelper;
import cn.edu.aust.common.util.LanguageUtil;
import cn.edu.aust.dto.BaseProblemDTO;
import cn.edu.aust.dto.ContestDTO;
import cn.edu.aust.dto.SolutionDTO;
import cn.edu.aust.pojo.entity.UserDO;
import cn.edu.aust.service.ContestProblemService;
import cn.edu.aust.service.ContestService;
import cn.edu.aust.service.ProblemService;
import cn.edu.aust.service.SolutionService;
import cn.edu.aust.service.UserService;
import cn.edu.aust.vo.SubmitTableVO;

/**
 * 判题控制器
 *
 * @author Niu Li
 * @since 2017/3/14
 */
@Controller
public class JudgerController {
  @Resource
  private ProblemService problemService;
  @Resource
  private UserService userService;
  @Resource
  private SolutionService solutionService;
  @Resource
  private ContestService contestService;
  @Resource
  private ContestProblemService contestProblemService;

  /**
   * 提交判题
   * @param id 题目id
   * @param sourceCode 源码
   * @param language 语言
   * @return 判题id
   */
  @PostMapping(value = "/judge/problem/{id}", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  @ResponseBody
  public ResultVO judger(@PathVariable("id") Long id,
      @RequestParam(value = "code") String sourceCode,
      @RequestParam(value = "lang") String language,
      @RequestParam(value = "contest_id",required = false) Long contestId) {
    //登录限制和参数检查
    UserDO loginUser = userService.getCurrent();
    ResultVO<Long> resultVO = new ResultVO<>();
    if (Objects.isNull(loginUser)){
      return resultVO.buildWithMsgAndStatus(PosCode.NO_LOGIN,"用户未登录");
    }
    if (StringUtils.isEmpty(sourceCode)){
      return resultVO.buildWithMsgAndStatus(PosCode.PARAM_ERROR,"源代码不能为空");
    }
    if (Objects.isNull(language)) {
      return resultVO.buildWithMsgAndStatus(PosCode.INTER_ERROR, "所选语言不能为空");
    }
    BaseProblemDTO problemDTO = problemService.findBasicById(id);
    if(Objects.isNull(problemDTO)){
      return resultVO.buildWithMsgAndStatus(PosCode.PARAM_ERROR,"所提交的题目不存在");
    }
    //竞赛题验证是否可以判题
    if (problemDTO.getType() == ProblemType.CONTEST.value){
      //判断是否验证过
      if (contestProblemService.isContainProblem(contestId,problemDTO.getId()) &&
          !contestService.isVisited(contestId, loginUser.getId())){
        return resultVO.buildWithMsgAndStatus(PosCode.PARAM_ERROR,"没权限判题");
      }
      //比赛是否过期
      ContestDTO contestDTO = contestService.findDetail(contestId);
      if (!contestService.canJudger(contestDTO)){
        return resultVO.buildWithMsgAndStatus(PosCode.PARAM_ERROR,"不在比赛时间");
      }
    }
    //浏览器端对加号支持有问题
    if (StringUtils.equals("C2", language)) {
      language = "C++";
    }
    LanguageUtil.Language lang = LanguageUtil.getLanguage(language);
    if (Objects.isNull(lang)){
      return resultVO.buildWithMsgAndStatus(PosCode.PARAM_ERROR,"所选语言不存在");
    }
    Long solutionId = solutionService.startJudger(loginUser.getId(), problemDTO,
        URLDecoder.decode(sourceCode), lang, contestId);
    return resultVO.buildOKWithData(solutionId);
  }

  /**
   * 查询用户提交列表
   * @return 查询结果
   */
  @GetMapping(value = "/judge/list", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  @ResponseBody
  public ResultVO judgeList(HttpServletRequest request){
    ResultVO<ResultVO.paginationData> resultVO = new ResultVO<>();
    //参数校验
    Integer pageSize = CgiHelper.getPageSize(request);
    Integer pageNum = CgiHelper.getPageNum(request);
    String search = CgiHelper.getString("search",null,request);
    UserDO loginUser = userService.getCurrent();
    if (Objects.isNull(loginUser)){
      return resultVO.buildWithMsgAndStatus(PosCode.NO_LOGIN, "用户未登录");
    }
    //查询列表
    PageInfo<SolutionDTO> data = solutionService.userSolutionList(search, loginUser.getId(),
        pageNum, pageSize);
    //构造返回
    List<SubmitTableVO> tableVOS = SubmitTableVO.assemble(data.getList());
    return resultVO.buildOKWithData(new ResultVO.paginationData<>(data.getTotal(),
        pageSize, tableVOS));
  }

  /**
   * 查询单条提交记录
   * @return 查询结果
   */
  @GetMapping(value = "/judge/{id}", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  @ResponseBody
  public ResultVO judgeOne(@PathVariable("id") Long solutionId){
    ResultVO<SubmitTableVO> resultVO = new ResultVO<>();
    //参数校验
    UserDO loginUser = userService.getCurrent();
    if (Objects.isNull(loginUser)){
      return resultVO.buildWithMsgAndStatus(PosCode.NO_LOGIN, "用户未登录");
    }
    //查询单条
    SolutionDTO solutionDTO = solutionService.findById(solutionId, loginUser.getId());
    if (Objects.isNull(solutionDTO)) {
      return resultVO.buildWithMsgAndStatus(PosCode.NO_PRIVILEGE, "用户无权限查看");
    }
    //构造返回
    return resultVO.buildOKWithData(SubmitTableVO.assembler(solutionDTO));
  }


}
