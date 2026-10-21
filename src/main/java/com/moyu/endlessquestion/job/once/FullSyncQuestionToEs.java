package com.moyu.endlessquestion.job.once;

import cn.hutool.core.collection.CollUtil;
import com.moyu.endlessquestion.esdao.QuestionEsDao;
import com.moyu.endlessquestion.model.dto.question.QuestionEsDTO;
import com.moyu.endlessquestion.model.entity.Question;
import com.moyu.endlessquestion.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;

import jakarta.annotation.Resource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.RefreshPolicy;
import org.springframework.data.elasticsearch.core.query.BulkOptions;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全量同步题目到 es
 *
 * @author MOYU
 */
// todo 取消注释开启任务
@Component
@Slf4j
public class FullSyncQuestionToEs implements CommandLineRunner {

    @Resource
    private QuestionService questionService;

//    @Resource
//    private QuestionEsDao questionEsDao;

    @Resource
    private ElasticsearchOperations elasticsearchOperations;

    @Override
    public void run(String... args) {
        // 全量获取题目（数据量不大的情况下使用）
        List<Question> questionList = questionService.list();
        if (CollUtil.isEmpty(questionList)) {
            return;
        }
        // 转为 ES 实体类
        List<QuestionEsDTO> questionEsDTOList = questionList.stream()
                .map(QuestionEsDTO::objToDto)
                .collect(Collectors.toList());
        // 分页批量插入到 ES
        final int pageSize = 500;
        int total = questionEsDTOList.size();
        log.info("FullSyncQuestionToEs start, total {}", total);
//        for (int i = 0; i < total; i += pageSize) {
//            // 注意同步的数据下标不能超过总数据量
//            int end = Math.min(i + pageSize, total);
//            log.info("sync from {} to {}", i, end);
//            questionEsDao.saveAll(questionEsDTOList.subList(i, end));
//        }
        //修改后的不强制刷新，等待 Elasticsearch 自动刷新

        BulkOptions bulkOptions = BulkOptions.builder()
                .withRefreshPolicy(RefreshPolicy.NONE)
                .build();

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

        log.info("FullSyncQuestionToEs end, total {}", total);
    }
}
