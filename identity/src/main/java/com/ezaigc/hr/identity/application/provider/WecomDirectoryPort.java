package com.ezaigc.hr.identity.application.provider;

import com.ezaigc.hr.identity.api.WecomDirectoryResultDTO;

public interface WecomDirectoryPort {
    WecomDirectoryResultDTO status();

    WecomDirectoryResultDTO preview();
}
