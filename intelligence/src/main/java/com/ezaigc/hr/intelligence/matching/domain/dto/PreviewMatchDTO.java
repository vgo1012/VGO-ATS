package com.ezaigc.hr.intelligence.matching.domain.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建即时匹配预览时使用的应用层命令。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreviewMatchDTO {

    /**
     * 职位描述（JD）版本 ID。
     */
    private String jdVersionId;
    /**
     * 简历版本 ID。
     */
    private String resumeVersionId;
    /**
     * 权重方案版本 ID。
     */
    private String weightProfileVersionId;
    private List<String> selectedCriterionIds;
}
