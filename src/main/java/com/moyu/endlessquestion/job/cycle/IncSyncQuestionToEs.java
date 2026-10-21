package com.moyu.endlessquestion.job.cycle;

import cn.hutool.core.collection.CollUtil;
import com.moyu.endlessquestion.esdao.QuestionEsDao;
import com.moyu.endlessquestion.mapper.QuestionMapper;
import com.moyu.endlessquestion.model.dto.question.QuestionEsDTO;
import com.moyu.endlessquestion.model.entity.Question;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.RefreshPolicy;
import org.springframework.data.elasticsearch.core.query.BulkOptions;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 增量同步题目到 es
 *
 * @author MOYU
 */
// todo 取消注释开启任务
@Component
@Slf4j
public class IncSyncQuestionToEs {

    @Resource
    private QuestionMapper questionMapper;

//    @Resource
//    private QuestionEsDao questionEsDao;

    @Resource
    private ElasticsearchOperations elasticsearchOperations;
    /**
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60 * 1000)
    public void run() {
        // 查询近 5 分钟内的数据
        long FIVE_MINUTES = 5 * 60 * 1000L;
        Date fiveMinutesAgoDate = new Date(new Date().getTime() - FIVE_MINUTES);
        List<Question> questionList = questionMapper.listQuestionWithDelete(fiveMinutesAgoDate);
        if (CollUtil.isEmpty(questionList)) {
            log.info("no inc question");
            return;
        }
        List<QuestionEsDTO> questionEsDTOList = questionList.stream()
                .map(QuestionEsDTO::objToDto)
                .collect(Collectors.toList());
        final int pageSize = 500;
        int total = questionEsDTOList.size();
        log.info("IncSyncQuestionToEs start, total {}", total);

        // 不主动刷新，等待 ES 自动刷新
        BulkOptions bulkOptions = BulkOptions.builder()
                .withRefreshPolicy(RefreshPolicy.NONE)
                .build();
//        for (int i = 0; i < total; i += pageSize) {
//            int end = Math.min(i + pageSize, total);
//            log.info("sync from {} to {}", i, end);
//            questionEsDao.saveAll(questionEsDTOList.subList(i, end));
//        }
        for (int i = 0; i < total; i += pageSize) {
            int end = Math.min(i + pageSize, total);

            List<IndexQuery> queries = questionEsDTOList
                    .subList(i, end)
                    .stream()
                    .map(dto -> new IndexQueryBuilder()
                            .withId(String.valueOf(dto.getId()))
                            .withObject(dto)
                            .build())
                    .collect(Collectors.toList());

            log.info("sync from {} to {}", i, end);

            elasticsearchOperations.bulkIndex(
                    queries,
                    bulkOptions,
                    QuestionEsDTO.class
            );
        }


        log.info("IncSyncQuestionToEs end, total {}", total);
    }
}