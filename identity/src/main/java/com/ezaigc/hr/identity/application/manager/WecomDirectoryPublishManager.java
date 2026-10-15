package com.ezaigc.hr.identity.application.manager;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.identity.api.WecomDepartmentDTO;
import com.ezaigc.hr.identity.api.WecomDirectoryResultDTO;
import com.ezaigc.hr.identity.api.WecomMemberDTO;
import com.ezaigc.hr.identity.application.support.WecomSnapshotRejectedException;
import com.ezaigc.hr.identity.application.support.WecomSnapshotValidator;
import com.ezaigc.hr.identity.config.WecomSyncProperties;
import com.ezaigc.hr.identity.domain.entity.WecomDepartmentEntity;
import com.ezaigc.hr.identity.domain.entity.WecomMemberEntity;
import com.ezaigc.hr.identity.domain.entity.WecomSyncBatchEntity;
import com.ezaigc.hr.identity.enums.WecomDirectoryVisibility;
import com.ezaigc.hr.identity.service.WecomDepartmentPersistenceService;
import com.ezaigc.hr.identity.service.WecomMemberPersistenceService;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class WecomDirectoryPublishManager {
    @Resource private WecomDepartmentPersistenceService departments;
    @Resource private WecomMemberPersistenceService members;
    @Resource private WecomSyncProperties properties;
    @Resource private ObjectMapper json;
    @Resource private PlatformIdGenerator ids;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(WecomDirectoryResultDTO snapshot, WecomSyncBatchEntity batch) {
        WecomSnapshotValidator.validate(snapshot, batch.getCorpId());
        Map<Long, WecomDepartmentEntity> previousDepartments = new HashMap<>();
        departments.list(Wrappers.<WecomDepartmentEntity>lambdaQuery().eq(WecomDepartmentEntity::getCorpId, batch.getCorpId()))
                .forEach(department -> previousDepartments.put(department.getDepartmentId(), department));
        Set<Long> incomingDepartments = new HashSet<>();
        snapshot.getDepartments().forEach(department -> incomingDepartments.add(department.getDepartmentId()));
        if (!incomingDepartments.containsAll(previousDepartments.keySet())) {
            throw new WecomSnapshotRejectedException("SCOPE_REDUCED", "可见部门范围缩小，需核实授权范围；未发布本批目录");
        }
        Map<String, WecomMemberEntity> previousMembers = new HashMap<>();
        members.list(Wrappers.<WecomMemberEntity>lambdaQuery().eq(WecomMemberEntity::getCorpId, batch.getCorpId()))
                .forEach(member -> previousMembers.put(member.getUserId(), member));
        Set<String> incomingMembers = new HashSet<>();
        snapshot.getMembers().forEach(member -> incomingMembers.add(member.getUserId()));
        long previouslyVisible = previousMembers.values().stream().filter(member -> WecomDirectoryVisibility.VISIBLE.matches(member.getDirectoryStatus())).count();
        long newlyMissing = previousMembers.values().stream().filter(member -> WecomDirectoryVisibility.VISIBLE.matches(member.getDirectoryStatus())
                && !incomingMembers.contains(member.getUserId())).count();
        if (previouslyVisible > 0 && newlyMissing * 100 >= previouslyVisible * properties.getMaxMemberDropPercent()) {
            throw new WecomSnapshotRejectedException("MEMBER_DROP", "可见人员数量骤降，需核实上游范围；未发布本批目录");
        }
        for (WecomDepartmentDTO source : snapshot.getDepartments()) {
            WecomDepartmentEntity existing = previousDepartments.get(source.getDepartmentId());
            WecomDepartmentEntity changed = new WecomDepartmentEntity();
            changed.setId(existing == null ? ids.newId() : existing.getId());
            changed.setCorpId(batch.getCorpId());
            changed.setDepartmentId(source.getDepartmentId());
            changed.setVersion(existing == null ? 0 : existing.getVersion());
            changed.setParentId(source.getParentId());
            changed.setName(source.getName());
            changed.setSourceOrder(source.getOrder());
            changed.setParentOutsideScope(source.isParentOutsideScope());
            changed.setChangedBatchId(batch.getId());
            if (existing == null) {
                requireWrite(departments.save(changed));
                batch.setInsertedCount(batch.getInsertedCount() + 1);
            } else if (existing.getParentId() != changed.getParentId() || !existing.getName().equals(changed.getName())
                    || existing.getSourceOrder() != changed.getSourceOrder()
                    || existing.isParentOutsideScope() != changed.isParentOutsideScope()) {
                requireWrite(departments.updateById(changed));
                batch.setChangedCount(batch.getChangedCount() + 1);
            }
        }
        for (WecomMemberDTO source : snapshot.getMembers()) {
            WecomMemberEntity existing = previousMembers.get(source.getUserId());
            WecomMemberEntity changed = new WecomMemberEntity();
            changed.setId(existing == null ? ids.newId() : existing.getId());
            changed.setCorpId(batch.getCorpId());
            changed.setUserId(source.getUserId());
            changed.setVersion(existing == null ? 0 : existing.getVersion());
            changed.setName(source.getName());
            changed.setPosition(source.getPosition() != null ? source.getPosition() : existing == null ? null : existing.getPosition());
            changed.setBusinessEmail(source.getBusinessEmail() != null ? source.getBusinessEmail()
                    : existing == null ? null : existing.getBusinessEmail());
            changed.setSourceStatus(source.getSourceStatus() != null ? source.getSourceStatus()
                    : existing == null ? null : existing.getSourceStatus());
            changed.setDepartmentIdsJson(json.writeValueAsString(source.getDepartmentIds().stream().sorted().toList()));
            changed.setDirectLeaderIdsJson(source.getDirectLeaderUserIds() != null
                    ? json.writeValueAsString(source.getDirectLeaderUserIds().stream().sorted().toList())
                    : existing == null ? null : existing.getDirectLeaderIdsJson());
            changed.setDirectoryStatus(WecomDirectoryVisibility.VISIBLE.getCode());
            changed.setChangedBatchId(batch.getId());
            if (existing == null) {
                requireWrite(members.save(changed));
                batch.setInsertedCount(batch.getInsertedCount() + 1);
            } else if (!sameMember(existing, changed)) {
                requireWrite(members.updateById(changed));
                batch.setChangedCount(batch.getChangedCount() + 1);
            }
        }
        for (WecomMemberEntity existing : previousMembers.values()) {
            if (!incomingMembers.contains(existing.getUserId())) {
                batch.setMissingCount(batch.getMissingCount() + 1);
                if (!WecomDirectoryVisibility.PENDING_VERIFICATION.matches(existing.getDirectoryStatus())) {
                    existing.setDirectoryStatus(WecomDirectoryVisibility.PENDING_VERIFICATION.getCode());
                    existing.setChangedBatchId(batch.getId());
                    requireWrite(members.updateById(existing));
                    batch.setChangedCount(batch.getChangedCount() + 1);
                }
            }
        }
        batch.setDepartmentCount(snapshot.getDepartments().size());
        batch.setMemberCount(snapshot.getMembers().size());
    }

    private boolean sameMember(WecomMemberEntity previous, WecomMemberEntity next) {
        return Objects.equals(previous.getName(), next.getName()) && Objects.equals(previous.getPosition(), next.getPosition())
                && Objects.equals(previous.getBusinessEmail(), next.getBusinessEmail())
                && Objects.equals(previous.getSourceStatus(), next.getSourceStatus())
                && sameJson(previous.getDepartmentIdsJson(), next.getDepartmentIdsJson())
                && sameJson(previous.getDirectLeaderIdsJson(), next.getDirectLeaderIdsJson())
                && Objects.equals(previous.getDirectoryStatus(), next.getDirectoryStatus());
    }

    private boolean sameJson(String previous, String next) {
        if (previous == null || next == null) return Objects.equals(previous, next);
        return json.readTree(previous).equals(json.readTree(next));
    }

    private void requireWrite(boolean written) {
        if (!written) throw new IllegalStateException("wecom-directory-write-conflict");
    }
}
