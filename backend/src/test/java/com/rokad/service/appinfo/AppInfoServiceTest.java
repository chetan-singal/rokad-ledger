package com.rokad.service.appinfo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppInfoServiceTest {

  // TC-090: the database name is read out of the connection address
  @Test
  void theDatabaseNameIsTakenFromTheConnectionAddress() {
    assertThat(AppInfoService.databaseNameOf("jdbc:mysql://localhost:3306/rokad_db"))
        .isEqualTo("rokad_db");
    assertThat(AppInfoService.databaseNameOf("jdbc:mysql://localhost:3306/rokad_db?useSSL=false"))
        .isEqualTo("rokad_db");
    assertThat(AppInfoService.databaseNameOf(null)).isEqualTo("unknown");
    assertThat(AppInfoService.databaseNameOf("jdbc:mysql://localhost:3306/")).isEqualTo("unknown");
  }
}
