package com.ezaigc.hr.intelligence.matching.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ezaigc.hr.intelligence.matching.domain.service.ProfessionalEvidenceMatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProfessionalEvidenceMatcherTest {
    @ParameterizedTest
    @CsvSource({
        "负责配电设备运行监测,配电设备监控", "监视暖通设备运行状态,暖通设备监控",
        "对库存进行盘点,库存盘点", "负责合同核查,合同审核",
        "配电设备监测,配电设备监控", "维护客户资料,客户资料维护"
    })
    void matchesEquivalentActionsOnlyForTheSameExplicitObject(String actual, String expected) {
        assertTrue(ProfessionalEvidenceMatcher.matches(actual, expected));
        assertTrue(ProfessionalEvidenceMatcher.matchesExperience(actual, expected));
    }

    @ParameterizedTest
    @CsvSource({
        "监测消防设备,配电设备监控", "设备监测,配电设备监控",
        "负责配电设备巡检,配电设备维修", "配电设备维修,配电设备监控",
        "计划监测配电设备,配电设备监控", "未监测配电设备,配电设备监控",
        "负责配电设备监测并维修消防设备,配电设备维修",
        "负责合同管理,合同审核", "监测设备,设备监控"
    })
    void doesNotInventObjectsActionsOrCompletedExperience(String actual, String expected) {
        assertFalse(ProfessionalEvidenceMatcher.matches(actual, expected));
        assertFalse(ProfessionalEvidenceMatcher.matchesExperience(actual, expected));
    }

    @ParameterizedTest
    @CsvSource({"了解配电设备监测原理,配电设备监控", "熟悉合同核查,合同审核"})
    void understandingEquivalentActionDoesNotEstablishExperience(String actual, String expected) {
        assertFalse(ProfessionalEvidenceMatcher.matchesExperience(actual, expected));
    }

    @ParameterizedTest
    @CsvSource({
        "使用LoRA完成模型微调,模型微调", "采用LoRA微调,模型微调",
        "模型微调,模型微调", "开发机器学习模型,机器学习",
        "完成深度学习模型训练,深度学习", "熟悉LoRA并采用LoRA完成模型微调,模型微调",
        "4bit量化,模型量化", "使用Milvus实现检索,向量数据库",
        "实现高并发服务,高并发", "使用自监督学习完成模型训练,自监督学习",
        "RAG 问答,RAG 问答", "数据清洗,数据清洗"
    })
    void requiresPracticeOrAnExplicitActivityFactForResponsibilities(String actual, String expected) {
        assertTrue(ProfessionalEvidenceMatcher.matchesExperience(actual, expected));
    }

    @ParameterizedTest
    @CsvSource({
        "阅读LoRA论文，了解微调原理,模型微调", "熟悉LoRA,模型微调",
        "LoRA,模型微调", "了解模型微调原理,模型微调",
        "学习模型微调,模型微调", "熟悉模型部署,模型部署",
        "未使用LoRA完成模型微调,模型微调", "Milvus,向量数据库",
        "阅读如何使用LoRA微调模型的教程,模型微调"
    })
    void knowledgeAloneDoesNotEstablishResponsibilityExperience(String actual, String expected) {
        assertFalse(ProfessionalEvidenceMatcher.matchesExperience(actual, expected));
    }

    @ParameterizedTest
    @CsvSource({
        "使用 Java 开发服务,Java", "Qwen2.5 微调,Qwen",
        "System Prompt 设计,提示词工程", "Prompt工程,提示词工程",
        "LoRA 微调 Qwen,模型微调", "SFT 训练,模型微调",
        "Milvus 检索,向量数据库", "pgvector,向量数据库",
        "基于 LangGraph 开发多节点工作流,Agent开发",
        "基于 LangGraph 开发多节点工作流,Agent智能体开发",
        "4bit量化,模型量化", "模型服务部署,服务化部署",
        "完成大模型服务化部署,模型部署",
        "RAG问答开发,RAG问答", "RAG问答系统开发,RAG问答开发",
        "通过模型微调降低错误,模型微调", "无中间件依赖的RAG问答系统,RAG问答",
        "开发无需人工介入的Agent工作流,Agent开发",
        "不熟悉TensorFlow；使用PyTorch开发模型,PyTorch"
    })
    void acceptsExplicitEvidenceAndBoundedCapabilityImplications(String actual, String expected) {
        assertTrue(ProfessionalEvidenceMatcher.matches(actual, expected));
    }

    @ParameterizedTest
    @CsvSource({
        "JavaScript,Java", "MongoDB,Go", "PyTorch,TensorFlow",
        "未使用LoRA,模型微调", "不熟悉Milvus,向量数据库",
        "未进行模型服务部署,模型服务部署", "尚未完成模型部署,模型部署",
        "不具备相关模型部署经验,模型部署", "Web服务化部署,模型部署",
        "服务化部署,模型服务部署", "Web服务部署,模型服务部署",
        "计划进行模型服务部署,模型部署",
        "没有Java经验,Java", "无模型微调经验,模型微调",
        "计划学习LangGraph开发工作流,Agent开发", "待学习Java,Java",
        "Java经验不足,Java", "LangGraph,Agent开发",
        "Python开发,模型服务部署", "开发系统,Agent开发",
        "RAG,图像识别", "RAG,模型微调", "Python,AI工程师",
        "RAG问答,医疗RAG问答", "Qwen,模型微调",
        "使用LoRA微调,模型量化", "向量数据库,Milvus",
        "LoRA微调,大语言模型选型、微调、优化与迭代"
    })
    void rejectsMissingOrUnrelatedEvidence(String actual, String expected) {
        assertFalse(ProfessionalEvidenceMatcher.matches(actual, expected));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "NULL|Java|false", "Java|NULL|false", "''|Java|false", "Java|''|false",
        "'   '|Java|false", "Java|'   '|false", "...|...|false",
        "JAVA|java|true", "负责 Java 开发|Java|true", "JavaScript|Java|false",
        "MongoDB|Go|false", "Java-17|Java|false", "Java/Go|Java|true",
        ".NET|.net|true", "使用 .NET 开发|.NET|true", "C++开发|C++|true",
        "C++17|C++|false", "C#工程师|C#|true", "XML|ML|false",
        "SQL/JSON|SQL|true", "SQL.JSON|SQL|false", "高级Java工程师|Java工程师|true",
        "高级 Java 工程师|Java工程师|true", "数据-分析|数据分析|true",
        "不相关|数据分析|false", "İstanbul|istanbul|true", "I|i|true"
    }, delimiter = '|', nullValues = "NULL")
    void preservesPublicTextBoundariesAndNullGuards(String actual, String expected, boolean matched) {
        assertEquals(matched, ProfessionalEvidenceMatcher.matches(actual, expected));
        assertEquals(matched, ProfessionalEvidenceMatcher.matchesExperience(actual, expected));
    }
}
