/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package it.cluster;

import io.thekraken.grok.api.Grok;
import io.thekraken.grok.api.Match;
import io.thekraken.grok.api.exception.GrokException;
import it.cluster.Cluster.Node;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.junit.BeforeClass;
import org.junit.Test;

import static it.cluster.Cluster.NodeType.CE;
import static it.cluster.Cluster.NodeType.ES;
import static it.cluster.Cluster.NodeType.WEB;
import static org.assertj.core.api.Assertions.assertThat;

public class DataCenterEditionTest {

  private static final Grok GROK = new Grok();

  @BeforeClass
  public static void initGrok() throws GrokException {
    GROK.addPatternFromReader(new InputStreamReader(DataCenterEditionTest.class.getResourceAsStream("/patterns.grok")));
    GROK.compile("(?m)(?<logdate>%{YEAR}.%{MONTHNUM}.%{MONTHDAY} %{HOUR}:%{MINUTE}:%{SECOND}) %{LOGLEVEL:loglevel}\\s++(?<message>.++)");
  }

  @Test
  public void start_stop_must_not_have_any_error() throws ExecutionException, InterruptedException {
    DataCenterEdition dce = new DataCenterEdition();
    dce.start();
    dce.getNodes().stream().forEach(DataCenterEditionTest::assertNoErrors);
    dce.stop();
    dce.getNodes().stream().forEach(DataCenterEditionTest::assertNoErrors);
  }

  @Test
  public void start_stop_must_not_have_any_error_with_DCE_minus_one_ES() throws ExecutionException, InterruptedException {
    Cluster cluster = Cluster.builder()
      .addNode(ES)
      .addNode(ES)
      .addNode(WEB, CE)
      .addNode(WEB, CE)
      .build();

    cluster.start();
    cluster.stop();
  }

  private static void assertNoErrors(Node node) {
    try {
      assertNoErrors(node.getOrchestrator().getServer().getAppLogs());
      assertNoErrors(node.getOrchestrator().getServer().getCeLogs());
      assertNoErrors(node.getOrchestrator().getServer().getWebLogs());
      assertNoErrors(node.getOrchestrator().getServer().getEsLogs());
    } catch (IOException e) {
      throw new RuntimeException("Unable to read log files", e);
    }
  }

  private static void assertNoErrors(File log) throws IOException {
    if (log != null && log.exists()) {
      // Not here we not doing multiline so line without correct format are discarded
      try (Stream<String> stream = Files.lines(log.toPath())) {
        stream.forEach(
          line -> {
            Match match = GROK.match(line);
            match.captures();
            assertThat(match.toMap().get("loglevel")).isNotEqualTo("ERROR");
          }
        );
      }
    }
  }
}
