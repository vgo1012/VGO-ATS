import type { ResumeIntake } from './api';

import { hasIntakeFailure } from './intakeFailureState';

const reasons:Record<string,string>={
  DOCUMENT_CONTAINER_INVALID:'文档内部结构无效，无法安全识别',
  DOCUMENT_PAGE_LIMIT:'文档页数超出安全上限',
  DOCUMENT_TEXT_LIMIT:'文档文字内容超出安全上限',
  MALWARE_OR_SIGNATURE_REJECTED:'安全检查发现风险，文件已隔离',
  MALWARE_SCANNER_UNAVAILABLE:'文件安全扫描服务暂不可用',
  INTAKE_PARSE_TEMPORARY_FAILURE:'简历解析处理发生临时异常',
  INTAKE_PARSE_INVALID:'简历内容或解析输入不符合处理要求',
  INTAKE_PARSE_ATTEMPTS_EXHAUSTED:'解析任务多次尝试后仍未完成',
  INTAKE_PROCESSING_RESULT_MISSING:'未能获取简历解析结果',
  INTAKE_COMPLETION_FAILED:'简历解析完成后的入库处理异常',
  INTAKE_ADOPTION_CANCELLING:'共享解析结果归档任务正在取消',
  INTAKE_ADOPTION_CANCELLED:'共享解析结果归档任务已取消',
  INTAKE_ADOPTION_STATUS_FAILED:'共享解析结果归档状态处理异常',
  MODEL_JSON_INVALID:'解析结果格式不正确，未能读取简历信息',
  MODEL_SCHEMA_INVALID:'AI 解析结果未通过结构校验',
  MODEL_DATE_INVALID:'解析结果中的日期格式不正确',
  MODEL_DATE_RANGE_INVALID:'解析结果中的起止日期不合理',
  MODEL_ITEM_ID_DUPLICATE:'解析结果包含重复记录',
  MODEL_SCORE_FIELD_FORBIDDEN:'解析结果包含不允许的评分字段',
  DATA_SCOPE_FORBIDDEN:'当前处理账号无权访问相关业务数据',
  DOCUMENT_CONTAINER_LIMIT:'文档资源或处理量超过当前安全上限',
  DOCUMENT_PAGE_RENDER_LIMIT:'文档页面或图片尺寸、像素数量不符合当前处理要求',
};
const evidenceCodes=new Set(['MODEL_EVIDENCE_BBOX_INVALID','MODEL_EVIDENCE_INVALID','MODEL_EVIDENCE_MISSING','MODEL_EVIDENCE_OFFSET_INVALID','MODEL_EVIDENCE_PAGE_INVALID','MODEL_EVIDENCE_POINTER_INVALID','MODEL_EVIDENCE_QUOTE_MISMATCH','MODEL_EVIDENCE_SOURCE_INVALID']);
const documentCorrectionCodes=new Set(['DOCUMENT_PAGE_RENDER_LIMIT','DOCUMENT_CONTAINER_LIMIT','DOCUMENT_CONTAINER_INVALID','DOCUMENT_PAGE_LIMIT','DOCUMENT_TEXT_LIMIT']);

export function IntakeFailure({value}:{value:ResumeIntake}) {
  if(!hasIntakeFailure(value))return null;
  if(value.status==='PARSING'&&value.processingStatus==='FAILED_RETRYABLE')return <div style={{marginTop:8,maxWidth:320}}>上次解析尝试未完成，系统将自动重试。</div>;
  const code=value.failureCode?.trim();
  const reason=code?(reasons[code]??(evidenceCodes.has(code)?'解析信息无法与原始简历内容对应':'暂无法解释该错误，请联系管理员核查')):'系统未提供具体原因';
  const advice=code==='DATA_SCOPE_FORBIDDEN'?'请管理员核对关联 JD、候选人归属及处理权限；权限调整后再重试。'
    :value.scoreStatus==='FAILED_RETRYABLE'?'可点击“重试评分”；若仍失败，请联系管理员排查。'
    :code&&documentCorrectionCodes.has(code)?'请压缩图片、精简内容或重新导出带文字层的 PDF 后上传，或联系管理员调整文档处理配置；文件或配置更新后再重试。'
    :value.status==='FAILED_RETRYABLE'?'可尝试重试导入；若提示不可重试，请联系管理员核查。'
      :value.status==='PARSING'&&value.processingStatus==='FAILED_RETRYABLE'?'系统正在等待自动重试。'
        :'请联系管理员核查后再处理。';
  return <div style={{marginTop:8,maxWidth:320,overflowWrap:'anywhere'}}><div>失败原因：{reason}</div><small>{advice}</small>{code?<details><summary>查看错误代码</summary><code>{code}</code></details>:null}</div>;
}
