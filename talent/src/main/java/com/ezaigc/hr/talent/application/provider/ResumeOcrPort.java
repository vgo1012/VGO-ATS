package com.ezaigc.hr.talent.application.provider;

import java.util.Optional;

public interface ResumeOcrPort {
  Optional<String> recognize(int pageNo, byte[] pngImage);
}
