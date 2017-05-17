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

import java.util.List;
import java.util.concurrent.ExecutionException;

import static it.cluster.Cluster.NodeType.CE;
import static it.cluster.Cluster.NodeType.ES;
import static it.cluster.Cluster.NodeType.WEB;

public class DataCenterEdition {
  private final Cluster cluster;

  public DataCenterEdition() {
    cluster = Cluster.builder()
      .addNode(ES)
      .addNode(ES)
      .addNode(ES)
      .addNode(WEB, CE)
      .addNode(WEB, CE)
      .build();
  }

  public void stop() throws ExecutionException, InterruptedException {
    cluster.stop();
  }

  public void start() throws ExecutionException, InterruptedException {
    cluster.start();
  }

  public List<Cluster.Node> getNodes() {
    return cluster.getNodes();
  }
}
