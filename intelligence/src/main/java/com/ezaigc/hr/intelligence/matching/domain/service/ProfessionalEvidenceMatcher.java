package com.ezaigc.hr.intelligence.matching.domain.service;

import static com.ezaigc.hr.intelligence.matching.domain.service.MatchingText.normalize;
import static com.ezaigc.hr.intelligence.matching.domain.service.MatchingText.similar;

import java.util.List;
import java.util.regex.Pattern;
import lombok.Value;
import lombok.experimental.Accessors;

/** 有限、可审计的职业能力映射；只使用简历明确证据，不推断相邻技能或业务领域。 */
public final class ProfessionalEvidenceMatcher {
    private static final Pattern NEGATIVE = Pattern.compile(
            "(?:尚未|从未|未|没有)(?:曾)?(?:使用|掌握|接触|参与|做过|开发|学习|进行|完成|实现|部署)|"
                    + "不(?:熟悉|了解|掌握|具备|会)|"
                    + "(?:没有|缺乏|缺少|尚无|暂无|无).{0,30}(?:经验|经历|能力|基础)|"
                    + "(?:计划|准备|打算|待|希望|将来)(?:学习|掌握|使用|开发|进行|完成|实现|部署)|"
                    + "(?:经验|经历|能力)(?:不足|欠缺)|"
                    + "\\b(?:no experience|not familiar|never used|plan to learn)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION = Pattern.compile("运行监视|运行监测|运行监控|监控|监测|监视|巡检|巡查|巡视|维修|修理|核查|审核|盘点|维护");
    private static final Pattern ACTION_NOT_PERFORMED = Pattern.compile(
            "(?:未|没有|不曾|尚未|从未|计划|准备|打算|希望|将来|待)(?:直接|实际|开展|进行)?"
                    + "(?:运行监视|运行监测|运行监控|监控|监测|监视|巡检|巡查|巡视|维修|修理|核查|审核|盘点|维护)");
    private static final Pattern IMPLEMENTATION = Pattern.compile("开发|实现|使用|采用|完成|构建|搭建|实施|负责");
    private static final Pattern KNOWLEDGE_ONLY = Pattern.compile(
            "阅读|了解|熟悉|原理|教程|(?:^|\\s)(?:正在)?学习");
    private static final Pattern ACTIVITY = Pattern.compile("微调|量化|部署|训练|设计|管理|测试|运维|优化|维护|分析");
    private static final Pattern QWEN_VERSION = Pattern.compile(
            "(?i)(?<![a-z0-9+#.\\-])qwen(?:[0-9]+(?:\\.[0-9]+)*)(?![a-z0-9])");
    private static final List<Capability> CAPABILITIES = List.of(
            new Capability(List.of("提示词工程", "Prompt工程", "Prompt Engineering", "System Prompt"),
                    List.of("提示词工程", "Prompt工程", "Prompt Engineering", "System Prompt")),
            new Capability(List.of("模型微调", "大模型微调", "模型微调训练"),
                    List.of("模型微调", "大模型微调", "LoRA", "QLoRA", "SFT")),
            new Capability(List.of("向量数据库"),
                    List.of("向量数据库", "Milvus", "pgvector", "ChromaDB")),
            new Capability(List.of("模型量化", "大模型量化"),
                    List.of("模型量化", "大模型量化", "4bit量化", "4-bit量化", "4 bit量化", "INT4量化")),
            new Capability(List.of("服务化部署"),
                    List.of("模型服务部署", "模型服务化部署", "服务化部署")),
            new Capability(List.of("模型部署", "模型服务部署", "模型服务化部署"),
                    List.of("模型部署", "模型服务部署", "模型服务化部署")),
            new Capability(List.of("RAG问答", "RAG问答开发", "RAG问答系统开发"),
                    List.of("RAG问答", "RAG问答开发", "RAG问答系统开发")));

    private ProfessionalEvidenceMatcher() {}

    /** 职责证据要求实施动作或明确活动标签，技术熟悉程度仅可用于技能维度。 */
    public static boolean matchesExperience(String actual, String expected) {
        if (actual == null || expected == null || normalize(expected).isEmpty()) {
            return false;
        }
        // 阅读/了解的宾语即使包含“使用、完成”也不是实施；并列实施句独立判断。
        for (String clause : actual.split("[，,。；;！!？?\\r\\n]|但是|但|不过|并且|并(?=采用|使用|完成|实现|开发|构建|搭建)|随后|然后")) {
            if (negative(clause)) {
                continue;
            }
            boolean implementation = IMPLEMENTATION.matcher(clause).find();
            boolean knowledgeOnly = KNOWLEDGE_ONLY.matcher(clause).find();
            boolean activity = ACTIVITY.matcher(clause).find();
            if (!knowledgeOnly && (similar(clause, expected) || equivalentAction(clause, expected)
                    || ((implementation || activity) && positiveMatch(clause, expected)))) {
                return true;
            }
        }
        return false;
    }

    public static boolean matches(String actual, String expected) {
        if (actual == null || expected == null || normalize(expected).isEmpty()) {
            return false;
        }
        // 否定只约束所在分句，不抹掉其他分句中的独立肯定事实。
        for (String clause : actual.split("[，,。；;！!？?\\r\\n]|但是|但|不过")) {
            if (!negative(clause) && positiveMatch(clause, expected)) {
                return true;
            }
        }
        return false;
    }

    private static boolean positiveMatch(String actual, String expected) {
        if (similar(actual, expected)) {
            return true;
        }
        if (equivalentAction(actual, expected)) {
            return true;
        }
        String normalizedExpected = normalize(expected);
        if (normalizedExpected.equals("qwen") && QWEN_VERSION.matcher(actual).find()) {
            return true;
        }
        for (Capability capability : CAPABILITIES) {
            if (capability.expected().stream().anyMatch(value -> normalize(value).equals(normalizedExpected))
                    && capability.evidence().stream().anyMatch(value -> similar(actual, value))) {
                return true;
            }
        }
        if (List.of("agent开发", "智能体开发", "agent智能体开发", "agent工作流开发", "智能体工作流开发")
                .contains(normalizedExpected)) {
            boolean technology = similar(actual, "LangGraph") || similar(actual, "Agent")
                    || similar(actual, "智能体");
            boolean workflow = actual.contains("工作流") || similar(actual, "workflow");
            boolean implementation = actual.contains("开发") || actual.contains("实现")
                    || actual.contains("构建") || actual.contains("搭建");
            return technology && workflow && implementation;
        }
        return false;
    }

    private static boolean negative(String clause) {
        return NEGATIVE.matcher(clause).find() || ACTION_NOT_PERFORMED.matcher(clause).find();
    }

    /** 只归一化动作和语法修饰；对象必须完整一致，不把“设备”等泛称作为能力证据。 */
    private static boolean equivalentAction(String actual, String expected) {
        String target = actionObject(expected);
        if (target == null) return false;
        for (String clause : actual.split("并且|以及|并|同时")) {
            if (!negative(clause) && target.equals(actionObject(clause))) return true;
        }
        return false;
    }

    private static String actionObject(String value) {
        var matcher = ACTION.matcher(value);
        if (!matcher.find()) return null;
        String action = matcher.group();
        String object = value.substring(0, matcher.start()) + value.substring(matcher.end());
        if (matcher.find()) return null;
        object = normalize(object).replaceFirst("^(?:负责|承担|执行|开展|进行|对)+", "")
                .replaceFirst("(?:运行状态|运行|工作)$", "")
                .replace("进行", "").replace("的", "");
        if (object.length() < 2 || List.of("设备", "系统", "项目", "工作", "业务", "设施", "资料")
                .contains(object)) return null;
        String canonical = switch (action) {
            case "运行监视", "运行监测", "运行监控", "监测", "监视" -> "监控";
            case "巡查", "巡视" -> "巡检";
            case "修理" -> "维修";
            case "核查" -> "审核";
            default -> action;
        };
        return canonical + ":" + object;
    }

    @Value
    @Accessors(fluent = true)
    private static class Capability {
        List<String> expected;
        List<String> evidence;
    }
}
