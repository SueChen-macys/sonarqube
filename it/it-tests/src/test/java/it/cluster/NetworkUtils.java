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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;

public class NetworkUtils {
  // TODO : move it to orchestrator ?
  // Same usage for ClusterSettingsLoopbackTest

  public static InetAddress getNonloopbackIPv4Address()  {
    try {
      Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
      for (NetworkInterface networkInterface : Collections.list(nets)) {
        if (!networkInterface.isLoopback() && networkInterface.isUp() && !isBlackListed(networkInterface)) {
          Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
          while (inetAddresses.hasMoreElements()) {
            InetAddress inetAddress = inetAddresses.nextElement();
            if (inetAddress instanceof Inet4Address) {
              return inetAddress;
            }
          }
        }
      }
    } catch (SocketException se) {
      throw new RuntimeException("Cannot find a non loopback card required for tests", se);
    }
    throw new RuntimeException("Cannot find a non loopback card required for tests");
  }

  private static boolean isBlackListed(NetworkInterface networkInterface) {
    return networkInterface.getName().startsWith("docker") ||
        networkInterface.getName().startsWith("vboxnet");
  }
}
