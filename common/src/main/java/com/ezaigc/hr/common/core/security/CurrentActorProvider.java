package com.ezaigc.hr.common.core.security;

import java.util.Optional;

/**
 * 当前操作人运行时能力提供器。
 */
@FunctionalInterface
public interface CurrentActorProvider {

    Optional<CurrentActor> currentActor();
}
