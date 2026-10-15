package com.ezaigc.hr.talent.application.provider;

import java.util.Optional;

public interface ResumeMultimodalPagePort {
  Optional<String> extractText(int pageNo, byte[] pngImage);
}
