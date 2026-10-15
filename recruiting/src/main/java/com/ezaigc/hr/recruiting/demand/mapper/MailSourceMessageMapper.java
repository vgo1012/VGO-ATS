package com.ezaigc.hr.recruiting.demand.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.recruiting.demand.domain.entity.MailSourceMessageEntity;
import org.apache.ibatis.annotations.Insert;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface MailSourceMessageMapper extends BaseMapper<MailSourceMessageEntity> {

  @SpecialSql("唯一键冲突保持原记录，以数据库原子插入保证并发邮件重放幂等")
  @Insert("""
    INSERT INTO demand_mail_source_message
    (id,jd_version_id,source_message_id,message_hash,payload_hash,sender_email,recipient_email,subject,plain_body,delivery_type,create_id,create_time,update_id,update_time)
    VALUES (#{id},#{jdVersionId},#{sourceMessageId},#{messageHash},#{payloadHash},#{senderEmail},#{recipientEmail},#{subject},#{plainBody},#{deliveryType},#{createId},#{createTime},#{updateId},#{updateTime})
    ON DUPLICATE KEY UPDATE id=id
    """)
  int insertIfAbsent(MailSourceMessageEntity row);

}
