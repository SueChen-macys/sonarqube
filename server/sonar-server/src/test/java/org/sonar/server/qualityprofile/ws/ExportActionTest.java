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
package org.sonar.server.qualityprofile.ws;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.profiles.ProfileExporter;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.qualityprofile.QualityProfileDto;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.language.LanguageTesting;
import org.sonar.server.organization.TestDefaultOrganizationProvider;
import org.sonar.server.qualityprofile.QProfileBackuper;
import org.sonar.server.qualityprofile.QProfileExporters;
import org.sonar.server.qualityprofile.QProfileRestoreSummary;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsActionTester;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

public class ExportActionTest {

  private static final String XOO_LANGUAGE = "xoo";
  private static final String JAVA_LANGUAGE = "java";

  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  private DbClient dbClient = db.getDbClient();
  private QProfileBackuper backuper = new TestBackuper();
  private QProfileWsSupport wsSupport = new QProfileWsSupport(dbClient, userSession, TestDefaultOrganizationProvider.from(db));

  @Test
  public void test_definition_without_exporters() {
    WebService.Action definition = newWsActionTester().getDef();

    assertThat(definition.isPost()).isFalse();
    assertThat(definition.isInternal()).isFalse();
    assertThat(definition.params()).extracting("key").containsOnly("language", "name", "organization");
    WebService.Param organizationParam = definition.param("organization");
    assertThat(organizationParam.since()).isEqualTo("6.4");
    assertThat(organizationParam.isInternal()).isTrue();
  }

  @Test
  public void test_definition_with_exporters() {
    WebService.Action definition = newWsActionTester(newExporter("polop"), newExporter("palap")).getDef();

    assertThat(definition.isPost()).isFalse();
    assertThat(definition.isInternal()).isFalse();
    assertThat(definition.params()).extracting("key").containsOnly("language", "name", "organization", "exporterKey");
    WebService.Param exportersParam = definition.param("exporterKey");
    assertThat(exportersParam.possibleValues()).containsOnly("polop", "palap");
    assertThat(exportersParam.deprecatedKey()).isEqualTo("format");
    assertThat(exportersParam.deprecatedKeySince()).isEqualTo("6.3");
    assertThat(exportersParam.isInternal()).isFalse();
  }

  @Test
  public void export_profile_in_default_organization() {
    QualityProfileDto profile = createProfile(db.getDefaultOrganization(), false);

    WsActionTester tester = newWsActionTester(newExporter("polop"), newExporter("palap"));
    String result = tester.newRequest()
      .setParam("language", profile.getLanguage())
      .setParam("name", profile.getName())
      .setParam("exporterKey", "polop").execute()
      .getInput();

    assertThat(result).isEqualTo("Profile " + profile.getLanguage() + "/" + profile.getName() + " exported by polop");
  }

  @Test
  public void export_profile_in_specified_organization() {
    OrganizationDto organization = db.organizations().insert();
    QualityProfileDto profile = createProfile(organization, false);

    WsActionTester tester = newWsActionTester(newExporter("polop"), newExporter("palap"));
    String result = tester.newRequest()
      .setParam("organization", organization.getKey())
      .setParam("language", profile.getLanguage())
      .setParam("name", profile.getName())
      .setParam("exporterKey", "polop").execute()
      .getInput();

    assertThat(result).isEqualTo("Profile " + profile.getLanguage() + "/" + profile.getName() + " exported by polop");
  }

  @Test
  public void throw_NotFoundException_if_specified_organization_does_not_exist() {
    WsActionTester tester = newWsActionTester(newExporter("foo"));

    expectedException.expect(NotFoundException.class);
    expectedException.expectMessage("No organization with key 'does_not_exist'");

    tester.newRequest()
      .setParam("organization", "does_not_exist")
      .setParam("language", XOO_LANGUAGE)
      .setParam("name", "bar")
      .setParam("exporterKey", "foo")
      .execute();
  }

  @Test
  public void export_default_profile() throws Exception {
    QualityProfileDto nonDefaultProfile = createProfile(db.getDefaultOrganization(), false);
    QualityProfileDto defaultProfile = createProfile(db.getDefaultOrganization(), true);

    WsActionTester tester = newWsActionTester(newExporter("polop"), newExporter("palap"));
    String result = tester.newRequest()
      .setParam("language", XOO_LANGUAGE)
      .setParam("exporterKey", "polop")
      .execute()
      .getInput();

    assertThat(result).isEqualTo("Profile " + defaultProfile.getLanguage() + "/" + defaultProfile.getName() + " exported by polop");
  }

  @Test
  public void throw_NotFoundException_if_profile_with_specified_name_does_not_exist_in_default_organization() throws Exception {
    expectedException.expect(NotFoundException.class);

    newWsActionTester().newRequest()
      .setParam("language", XOO_LANGUAGE)
      .setParam("exporterKey", "polop").execute();
  }

  @Test
  public void throw_IAE_if_export_with_specified_key_does_not_exist() throws Exception {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Value of parameter 'exporterKey' (unknown) must be one of: [polop, palap]");

    newWsActionTester(newExporter("polop"), newExporter("palap")).newRequest()
      .setParam("language", XOO_LANGUAGE)
      .setParam("exporterKey", "unknown").execute();
  }

  @Test
  public void return_backup_when_exporter_is_not_specified() throws Exception {
    OrganizationDto organization = db.getDefaultOrganization();
    QualityProfileDto profile = createProfile(organization, false);

    String result = newWsActionTester(newExporter("polop")).newRequest()
      .setParam("language", profile.getLanguage())
      .setParam("name", profile.getName())
      .execute()
      .getInput();

    assertThat(result).isEqualTo("Backup of " + profile.getLanguage() + "/" + profile.getKey());
  }

  @Test
  public void do_not_mismatch_profiles_with_other_organizations_and_languages() {
    OrganizationDto org1 = db.organizations().insert();
    OrganizationDto org2 = db.organizations().insert();
    QualityProfileDto defaultJavaInOrg1 = db.qualityProfiles().insert(org1, p -> p.setLanguage(JAVA_LANGUAGE), p -> p.setDefault(true), p -> p.setName("Sonar Way"));
    QualityProfileDto nonDefaultJavaInOrg1 = db.qualityProfiles().insert(org1, p -> p.setLanguage(JAVA_LANGUAGE), p -> p.setDefault(false), p -> p.setName("My Way"));
    QualityProfileDto defaultXooInOrg1 = db.qualityProfiles().insert(org1, p -> p.setLanguage(XOO_LANGUAGE), p -> p.setDefault(true), p -> p.setName("Sonar Way"));
    QualityProfileDto nonDefaultXooInOrg1 = db.qualityProfiles().insert(org1, p -> p.setLanguage(XOO_LANGUAGE), p -> p.setDefault(false), p -> p.setName("My Way"));
    QualityProfileDto defaultJavaInOrg2 = db.qualityProfiles().insert(org2, p -> p.setLanguage(JAVA_LANGUAGE), p -> p.setDefault(true), p -> p.setName("Sonar Way"));
    QualityProfileDto nonDefaultJavaInOrg2 = db.qualityProfiles().insert(org2, p -> p.setLanguage(JAVA_LANGUAGE), p -> p.setDefault(false), p -> p.setName("My Way"));
    QualityProfileDto defaultXooInOrg2 = db.qualityProfiles().insert(org2, p -> p.setLanguage(XOO_LANGUAGE), p -> p.setDefault(true), p -> p.setName("Sonar Way"));
    QualityProfileDto nonDefaultXooInOrg2 = db.qualityProfiles().insert(org2, p -> p.setLanguage(XOO_LANGUAGE), p -> p.setDefault(false), p -> p.setName("My Way"));

    WsActionTester tester = newWsActionTester();

    // default profile for specified organization and language
    assertThat(tester.newRequest()
      .setParam("organization", org1.getKey())
      .setParam("language", defaultJavaInOrg1.getLanguage())
      .execute()
      .getInput())
        .isEqualTo("Backup of java/" + defaultJavaInOrg1.getKey());

    // profile for specified organization, language and name --> do not mix with Xoo profile or profile with same lang/name on other
    // organization
    assertThat(tester.newRequest()
      .setParam("organization", org1.getKey())
      .setParam("language", defaultJavaInOrg1.getLanguage())
      .setParam("name", defaultJavaInOrg1.getName())
      .execute()
      .getInput())
        .isEqualTo("Backup of java/" + defaultJavaInOrg1.getKey());
  }

  private QualityProfileDto createProfile(OrganizationDto organization, boolean isDefault) {
    return db.qualityProfiles().insert(organization, p -> p.setLanguage(XOO_LANGUAGE), p -> p.setDefault(isDefault));
  }

  private WsActionTester newWsActionTester(ProfileExporter... profileExporters) {
    QProfileExporters exporters = new QProfileExporters(dbClient, null, null, profileExporters, null);
    return new WsActionTester(new ExportAction(dbClient, backuper, exporters, LanguageTesting.newLanguages(XOO_LANGUAGE, JAVA_LANGUAGE), wsSupport));
  }

  private static ProfileExporter newExporter(String key) {
    return new ProfileExporter(key, StringUtils.capitalize(key)) {
      @Override
      public String getMimeType() {
        return "text/plain+" + key;
      }

      @Override
      public void exportProfile(RulesProfile profile, Writer writer) {
        try {
          writer.write(format("Profile %s/%s exported by %s", profile.getLanguage(), profile.getName(), key));
        } catch (IOException ioe) {
          throw new RuntimeException(ioe);
        }
      }
    };
  }

  private static class TestBackuper implements QProfileBackuper {

    @Override
    public void backup(DbSession dbSession, QualityProfileDto profile, Writer backupWriter) {
      try {
        backupWriter.write(format("Backup of %s/%s", profile.getLanguage(), profile.getKey()));
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public QProfileRestoreSummary restore(DbSession dbSession, Reader backup, OrganizationDto organization, @Nullable String overriddenProfileName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public QProfileRestoreSummary restore(DbSession dbSession, Reader backup, QualityProfileDto profile) {
      throw new UnsupportedOperationException();
    }
  }
}
